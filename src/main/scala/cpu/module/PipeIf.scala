package hpipe

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._
import hammer._
import hpipe.Insts._

class PipeIfIO(implicit p: HPipeParameters) extends StageIO {
  val fetch   = new InstFetchIO
  val regRead = Flipped(new RegFileReadPort)

  val toId   = Output(new If2IdIO)
  val fromEx = Input(new BranchInfo)

  val feedForwardMem = Input(new DestInfo)
  val feedForwardEx  = Input(new DestInfo)
  val feedForwardId  = Input(new DestInfo)

  val stall     = Input(Bool())
  val interrupt = Input(Bool())

  val csr = Input(new Csr)
}

class PipeIf(implicit val p: HPipeParameters) extends Module {
  val io = IO(new PipeIfIO)

  val pc = RegInit(UInt(p.AddrWidth.W), p.ResetVector.U)

  // Decode BR & JAL for BTB
  val inst = io.fetch.inst

  def parse(
      br:   Boolean,
      jal:  Boolean,
      jalr: Boolean,
      mret: Boolean,
  ) =
    BitPat(
      s"b${if (br) 1 else 0}"
        ++ s"${if (jal) 1 else 0}"
        ++ s"${if (jalr) 1 else 0}"
        ++ s"${if (mret) 1 else 0}",
    )

  val table = TruthTable(
    Map(
      JAL  -> parse(false, true, false, false),
      JALR -> parse(false, false, true, false),
      BEQ  -> parse(true, false, false, false),
      BNE  -> parse(true, false, false, false),
      BLT  -> parse(true, false, false, false),
      BGE  -> parse(true, false, false, false),
      BLTU -> parse(true, false, false, false),
      BGEU -> parse(true, false, false, false),
      MRET -> parse(false, false, false, true),
    ),
    BitPat.N(4),
  )
  val decoded = decoder(inst, table)
  val isBr    = decoded.msb()
  val isJal   = decoded.msb(1)
  val isJalr  = decoded.msb(2)
  val isMRet  = decoded.msb(3)

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
  val rdAddr  = inst(11, 7)
  io.regRead.addr := rs1Addr

  val ffId  = io.feedForwardId
  val ffEx  = io.feedForwardEx
  val ffMem = io.feedForwardMem

  val rs1InId  = ffId.gprMatch(rs1Addr)
  val rs1InEx  = ffEx.gprMatch(rs1Addr)
  val rs1InMem = ffMem.gprMatch(rs1Addr)

  val rs1 = MuxIf(
    rs1InEx  -> ffEx.gpr.bits.data,
    rs1InMem -> ffMem.gpr.bits.data,
  )(io.regRead.data)

  // RS1 should be valid for JALR to take branch
  // Invalid cases:
  // 1. An inst in ID stage will write to rs1, but ID stage doesn't calculate
  // 2. An inst in EX stage feeds forward, but it's a ld inst
  val rs1Valid = !rs1InId && !(rs1InEx && ffEx.gpr.bits.isLd)

  val brAddr = UIntCLA(32)(Mux(isJalr, rs1, pc), imm, 0.B).end(32)

  val predictor = Module(new BranchPredictor)
  val read      = predictor.io.read
  read.pc            := pc
  read.brAddr        := brAddr
  read.defaultTarget := pc +% 4.U
  read.info.isJalr   := isJalr
  read.info.isJal    := isJal
  read.info.isBr     := isBr
  read.info.isCall   :=
    (isJal || isJalr) && (rdAddr === 1.U || rdAddr === 5.U)
  read.info.isRet :=
    (isJalr
      && !(rs1Addr === rdAddr)
      && (rs1Addr === 1.U || rs1Addr === 5.U)
      && !inst(31, 20).orR)
  read.rs1Valid := rs1Valid

  val write = predictor.io.write
  write.info     := io.fromEx.flags
  write.pc       := io.fromEx.pc
  write.brTake   := io.fromEx.brTake
  write.callAddr := io.fromEx.callAddr

  val mepcInId  = ffId.csrMatch(CsrAddr.MEPC)
  val mepcInEx  = ffEx.csrMatch(CsrAddr.MEPC)
  val mepcInMem = ffMem.csrMatch(CsrAddr.MEPC)

  val mepc = MuxIf(
    mepcInEx  -> ffEx.csr.bits.data,
    mepcInMem -> ffMem.csr.bits.data,
  )(io.csr.mepc)

  val mepcValid = !mepcInId

  val nextpc = MuxIf(
    // We don't need feed-forward here, as interrupt will flush everything
    io.interrupt           -> io.csr.mtvec,
    (isMRet && !mepcValid) -> pc,
    isMRet                 -> mepc,
    io.stall               -> pc,
    io.fromEx.redirect     -> io.fromEx.target,
  )(predictor.io.read.target)

  pc            := nextpc
  io.fetch.addr := pc

  val toId = io.toId
  toId.valid := !reset.asBool
  toId.pc    := pc
  toId.inst  := io.fetch.inst

  toId.prediction.flags         := predictor.io.read.info
  toId.prediction.jalrSrc       := rs1Addr
  toId.prediction.brTake        := predictor.io.read.brTake
  toId.prediction.defaultTarget := predictor.io.read.defaultTarget
  toId.prediction.target        := predictor.io.read.target

  io.busy := isMRet && !mepcValid
}
