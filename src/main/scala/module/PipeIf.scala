package hpipe

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._
import hammer._
import hpipe.Insts._

class PipeIfIO(implicit p: HPipeParameters) extends StageIO {
  val fetch = new InstFetchIO

  val toId   = Output(new If2IdIO)
  val fromEx = Input(new BranchInfo)

  val feedForwardMem = Input(new DestInfo)
  val feedForwardEx  = Input(new DestInfo)
  val feedForwardSg  = Input(new DestInfo)
  val feedForwardId  = Input(new DestInfo)

  val stall = Input(Bool())
  val trap  = Input(Bool())

  val csr = Input(new Csr)
}

class PipeIf(implicit val p: HPipeParameters) extends Module {
  val io = IO(new PipeIfIO)

  val pc = RegInit(UInt(p.AddrWidth.W), p.ResetVector.U)

  // Decode BR & JAL for BTB
  val inst = io.fetch.inst

  def parse(
      jal:  Boolean,
      jalr: Boolean,
      mret: Boolean,
  ) =
    BitPat(
      s"b${if (jal) 1 else 0}"
        ++ s"${if (jalr) 1 else 0}"
        ++ s"${if (mret) 1 else 0}",
    )

  val table = TruthTable(
    Map(
      JAL  -> parse(true, false, false),
      JALR -> parse(false, true, false),
      MRET -> parse(false, false, true),
    ),
    BitPat.N(4),
  )
  val decoded = decoder(inst, table)
  val isJal   = decoded.msb()
  val isJalr  = decoded.msb(1)
  val isMRet  = decoded.msb(2)

  val imm = MuxIf(
    isJalr -> SignExt(inst(31, 20), 32),
    isJal  -> SignExt(
      inst(31) ## inst(19, 12) ## inst(20) ## inst(30, 21) ## 0.U(1.W),
      32,
    ),
  )(0.U)

  // Addr Gen
  val rs1Addr = inst(19, 15)
  val rdAddr  = inst(11, 7)

  val jalAddr = pc +% imm

  val predictor = Module(new BranchPredictor)
  val brRead    = predictor.io.read
  brRead.pc           := pc
  brRead.flags.isJal  := isJal
  brRead.flags.isCall := (isJal || isJalr) && (rdAddr === 1.U || rdAddr === 5.U)
  brRead.flags.isRet  :=
    (isJalr
      && !(rs1Addr === rdAddr)
      && (rs1Addr === 1.U || rs1Addr === 5.U)
      && !inst(31, 20).orR)
  brRead.jalAddr := jalAddr

  val brWrite = predictor.io.write
  brWrite.pc     := io.fromEx.pc
  brWrite.flags  := io.fromEx.flags
  brWrite.valid  := io.fromEx.valid
  brWrite.target := io.fromEx.target
  brWrite.take   := io.fromEx.take

  // Csr Forwarding
  val ffId  = io.feedForwardId
  val ffSg  = io.feedForwardSg
  val ffEx  = io.feedForwardEx
  val ffMem = io.feedForwardMem

  val mepcInId  = ffId.csrMatch(CsrAddr.MEPC)
  val mepcInSg  = ffSg.csrMatch(CsrAddr.MEPC)
  val mepcInEx  = ffEx.csrMatch(CsrAddr.MEPC)
  val mepcInMem = ffMem.csrMatch(CsrAddr.MEPC)

  val mepc = MuxIf(
    mepcInEx  -> ffEx.csr.bits.data,
    mepcInMem -> ffMem.csr.bits.data,
  )(io.csr.mepc)

  val mepcValid = !mepcInId && !mepcInSg

  val stepPc = pc +% 4.U
  val nextPc = MuxIf(
    // We don't need feed-forward here, as trap will flush everything
    io.trap               -> io.csr.mtvec,
    (isMRet && mepcValid) -> mepc,
    (isMRet || io.stall)  -> pc,
    io.fromEx.redirect    -> io.fromEx.redirectTarget,
    brRead.take           -> brRead.target,
  )(stepPc)

  pc            := nextPc
  io.fetch.addr := pc

  val toId = io.toId
  toId.valid := !reset.asBool
  toId.pc    := pc
  toId.inst  := io.fetch.inst

  val pred = toId.prediction
  pred.flags  := brRead.flags
  pred.take   := brRead.take
  pred.target := brRead.target
  pred.stepPc := stepPc

  io.busy := isMRet && !mepcValid
}
