package hpipe

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._
import hammer._
import hpipe.Insts._

class PipeIfIO(implicit p: HPipeParameters) extends StageIO {
  val fetch = new InstFetchPort

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

class PipeIf(implicit val p: HPipeParameters)
    extends StageModule(new PipeIfIO) {

  // Inst Fetch State Machine
  val pc        = RegInit(UInt(p.AddrWidth.W), p.ResetVector.U)
  val fetchBusy = RegZero(Bool())

  io.fetch.addr.valid := !fetchBusy
  io.fetch.addr.bits  := pc
  io.fetch.inst.ready := true.B

  val inst     = io.fetch.inst.bits
  val instFire = io.fetch.inst.fire

  /**
    * The pcFetching bit indicates whether a pc req is sent and not yet received
    * It follows the truth table below:
    * 
    * addr  |   inst    |   result
    * ==============================
    *  0    |    0      |   keep
    *  1    |    0      |   1
    *  0    |    1      |   0
    *  1    |    1      |   keep
    * 
    * So when (addr ^ inst), pcFetching = addr
    * else its value is kept
    */
  fetchBusy := MuxIf(
    io.fromEx.redirect                        -> 0.B,
    (io.fetch.addr.fire ^ io.fetch.inst.fire) -> io.fetch.addr.fire,
  )(fetchBusy)

  // Inst fetch is only valid when
  // 1. A fetch is in flight, then the response is fired (fetchBusy && inst.fire)
  // 2. A fetch and its response is fired in the same cycle (addr.fire && inst.fire)
  // If PC changes when a fetch is in flight (branch), the fetchBusy will be pulled down by MuxIf
  val fetchValid = io.fetch.inst.fire && (fetchBusy || io.fetch.addr.fire)

  // Decode BR & JAL for BTB
  val decoder = Module(new BranchDecoder)
  decoder.io.inst := inst
  val decoded = decoder.io.out
  val isJal   = decoded.isJal
  val isJalr  = decoded.isJalr
  val isMret  = decoded.isMret

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

  val busy = (isMret && !mepcValid) || !fetchValid
  val halt = io.stall

  val stepPc = pc +% 4.U
  val nextPc = MuxIf(
    // We don't need feed-forward here, as trap will flush everything
    io.trap               -> io.csr.mtvec,
    (isMret && mepcValid) -> mepc,
    io.fromEx.redirect    -> io.fromEx.redirectTarget,
    halt                  -> pc,
    brRead.take           -> brRead.target,
  )(stepPc)

  pc := nextPc

  val toId = io.toId
  toId.valid := !reset.asBool && fetchValid
  toId.pc    := pc
  toId.inst  := inst

  val pred = toId.prediction
  pred.flags  := brRead.flags
  pred.take   := brRead.take
  pred.target := brRead.target
  pred.stepPc := stepPc

  io.busy := busy
}
