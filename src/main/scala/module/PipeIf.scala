package hpipe

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._
import hammer._
import hpipe.branch._
import hpipe.decode._
import hpipe.decode.Insts._

class PipeIfIO(implicit p: HPipeParameters) extends StageIO {
  val inst = new MemReadPort

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

  val pcMisaligned = pc.end(p.PcUnusedWidth).orR

  io.inst.addr.valid := !fetchBusy
  io.inst.addr.bits  := pc
  io.inst.resp.ready := true.B

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
    io.fromEx.redirect                      -> 0.B,
    (io.inst.addr.fire ^ io.inst.resp.fire) -> io.inst.addr.fire,
  )(fetchBusy)

  // Inst fetch is only valid when
  // 1. A fetch is in flight, then the response is fired (fetchBusy && inst.fire)
  // 2. A fetch and its response is fired in the same cycle (addr.fire && inst.fire)
  // If PC changes when a fetch is in flight (branch), the fetchBusy will be pulled down by MuxIf
  val fetchDone    = io.inst.resp.fire && (fetchBusy || io.inst.addr.fire)
  val lastFetch    = RegEnable(io.inst.resp.bits, fetchDone)
  val currentFetch = Mux(fetchDone, io.inst.resp.bits, lastFetch)

  val instRaw = currentFetch.data
  val isC     = !instRaw.end(2).andR && p.ExtC.B
  val inst    = if (p.ExtC) {
    val decomp = Module(new RvcDecompressor)
    decomp.io.in := instRaw.end(16)
    Mux(isC, decomp.io.out, instRaw)
  } else instRaw

  // Decode BR & JAL for BTB
  val decoder = Module(new EarlyDecoder)
  decoder.io.pc   := pc
  decoder.io.inst := inst
  val decoded = decoder.io.out

  val predictor = Module(new BranchPredictor)
  val brRead    = predictor.io.read
  brRead.pc             := pc
  brRead.flags.isUncond := decoded.isUncond
  brRead.uncondAddr     := decoded.uncondAddr
  brRead.flags.isCall   := decoded.isCall
  brRead.flags.isRet    := decoded.isRet

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

  val busy = (decoded.isMret && !mepcValid) || !fetchDone
  val halt = io.stall

  val stepPc = pc +% Mux(isC, 2.U, 4.U)
  val nextPc = MuxIf(
    // We don't need feed-forward here, as trap will flush everything
    io.trap                       -> io.csr.mtvec,
    (decoded.isMret && mepcValid) -> mepc,
    io.fromEx.redirect            -> io.fromEx.redirectTarget,
    halt                          -> pc,
    brRead.take                   -> brRead.target,
  )(stepPc)

  pc := nextPc

  val toId = io.toId
  toId.valid      := !reset.asBool && fetchDone
  toId.pc         := pc
  toId.inst       := inst
  toId.isC        := isC
  toId.trap.valid := pcMisaligned || currentFetch.excp
  toId.trap.cause := MuxIf(
    pcMisaligned      -> 0.U,
    currentFetch.excp -> 1.U,
  )(0.U)

  val pred = toId.pred
  pred.flags  := brRead.flags
  pred.take   := brRead.take
  pred.target := brRead.target
  pred.stepPc := stepPc

  io.busy := busy
}
