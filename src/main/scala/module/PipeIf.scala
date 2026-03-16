package hpipe

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._
import hammer._
import hpipe.Insts._

class PipeIfIO(implicit p: Parameters) extends StageIO {
  val fetch   = new InstFetchIO
  val regRead = Flipped(new RegFileReadPort)

  val toId   = new If2IdIO
  val fromEx = Input(new BranchFeedback)

  val feedForwardMem = Input(new FeedForward)
  val feedForwardEx  = Input(new FeedForward)
  val feedForwardId  = Input(new FeedForward)

  val stall = Input(Bool())
}

class PipeIf(implicit p: Parameters) extends Module {
  val io = IO(new PipeIfIO)

  io.busy := false.B

  val pc = RegInit(UInt(p.AddrWidth.W), p.ResetVector.U)

  // Decode BR & JAL for BTB
  val inst = io.fetch.inst

  def parse(
      br:   Boolean,
      jal:  Boolean,
      jalr: Boolean,
  ) =
    BitPat(
      s"b${if (br) 1 else 0}"
        ++ s"${if (jal) 1 else 0}"
        ++ s"${if (jalr) 1 else 0}",
    )

  val table = TruthTable(
    Map(
      JAL  -> parse(false, true, false),
      JALR -> parse(false, false, true),
      BEQ  -> parse(true, false, false),
      BNE  -> parse(true, false, false),
      BLT  -> parse(true, false, false),
      BGE  -> parse(true, false, false),
      BLTU -> parse(true, false, false),
      BGEU -> parse(true, false, false),
    ),
    BitPat(0.U(3.W)),
  )
  val decoded = decoder(inst, table)
  val isBr    = decoded.msb()
  val isJal   = decoded.msb(1)
  val isJalr  = decoded.msb(2)

  val imm = MuxIf(
    isJalr -> SignExt(inst(31, 20), 32),
    isJal  -> SignExt(
      inst(31) ## inst(19, 12) ## inst(20) ## inst(30, 21) ## 0.U(1.W),
      32,
    ),
    isBr -> SignExt(
      inst(31) ## inst(7) ## inst(30, 25) ## inst(11, 8) ## 0.U(1.W),
      32,
    ),
  )(0.U)

  // Addr Gen
  val rs1Addr = inst(19, 15)
  io.regRead.addr := rs1Addr

  val ffId  = io.feedForwardId.isValid(rs1Addr)
  val ffEx  = io.feedForwardEx.isValid(rs1Addr) && !io.feedForwardEx.isLd
  val ffMem = io.feedForwardMem.isValid(rs1Addr)

  val rs1 = MuxIf(
    ffEx  -> io.feedForwardEx.data,
    ffMem -> io.feedForwardEx.data,
  )(io.regRead.data)

  // RS1 should be valid for JALR to take branch
  // Invalid cases:
  // 1. An inst in ID stage will write to rs1, but ID stage doesn't calculate
  // 2. An inst in EX stage feeds forward, but it's a ld inst
  val rs1Valid = !ffId && !(ffEx && io.feedForwardEx.isLd)

  val brAddr = UIntCLA(32)(Mux(isJalr, rs1, pc), imm, 0.B).end(32)

  val btb = Module(new BranchPredictor)
  btb.io.read.pc     := pc
  btb.io.read.brAddr := brAddr
  btb.io.read.isJalr :=
    isJalr && rs1Valid // If rs1 is not ready, cancel HistBuffer prediction
  btb.io.read.isJal := isJal
  btb.io.read.isBr  := isBr

  btb.io.write.pc   := io.fromEx.pc
  btb.io.write.take := io.fromEx.take
  btb.io.write.isBr := io.fromEx.isBr

  val nextpc = MuxIf(
    io.stall           -> pc,
    io.fromEx.redirect -> io.fromEx.addr,
  )(btb.io.read.nextpc)

  pc := nextpc

  io.fetch.addr  := pc
  io.toId.valid  := !reset.asBool
  io.toId.pc     := pc
  io.toId.inst   := io.fetch.inst
  io.toId.brTake := btb.io.read.take
}
