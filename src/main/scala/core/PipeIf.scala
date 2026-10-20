package hpipe

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._
import hammer._
import hpipe.branch._
import hpipe.decode._
import hpipe.decode.Insts._

class PcReqInfo(implicit p: HPipeParameters) extends Bundle {
  val pc             = Addr()
  val pcInMiddle     = Bool()
  val epoch          = UInt(p.EpochWidth.W)
  val pred           = new BranchPredictInfo
  val predPcInMiddle = Bool()
}

class PipeIfIO(implicit p: HPipeParameters) extends StageIO {
  val inst = new CoreReadPort

  val toId = Output(new If2IdIO)

  val branch = Flipped(Valid(new BranchInfo))

  val stall = Input(Bool())
  val trap  = Input(Bool())

  val csr = Input(new Csr)
}

class PipeIf(implicit val p: HPipeParameters)
    extends StageModule(new PipeIfIO) {
  val req    = io.inst.req
  val resp   = io.inst.resp
  val toId   = io.toId
  val branch = io.branch
  val csr    = io.csr

  def align4(addr: UInt) = addr.head(p.XLEN - 2) ## 0.U(2.W)

  // State
  val sIdle :: sWfi :: Nil = Enum(2)
  val state                = RegInit(sIdle)
  val isIdle               = state === sIdle
  val isWfi                = state === sWfi

  val stall = io.stall || isWfi
  val flush = io.trap || branch.bits.redirect

  // Pc
  val pc     = RegInit(Addr(), p.ResetVector.U)
  val stepPc = align4(pc +% 4.U)

  val pcInMiddle = pc(1)

  val pred = Module(new BranchPredictor)
  pred.io.read.pc := align4(pc)
  pred.io.write   := branch

  val brTake = pred.io.read.take && !pcInMiddle

  pc := MuxIf(
    io.trap              -> io.csr.mtvec,
    branch.bits.redirect -> branch.bits.target,
    (stall || !req.fire) -> pc,
    brTake               -> pred.io.read.target,
  )(stepPc)

  // Req
  val sent = RegZero(Bool())
  sent := MuxIf(
    flush                  -> false.B,
    (req.fire ^ resp.fire) -> req.fire,
  )(sent)

  val reqEpoch = RegZero(UInt(p.EpochWidth.W))
  reqEpoch := Mux(req.fire, reqEpoch +% 1.U, reqEpoch)

  val realignReady = WireZero(Bool())
  req.valid     := (!sent || resp.fire) && realignReady && !flush
  req.bits.addr := align4(pc)

  val inflight = RegZero(new PcReqInfo)
  when(req.fire) {
    inflight.pc             := align4(pc)
    inflight.pcInMiddle     := pcInMiddle
    inflight.epoch          := reqEpoch
    inflight.pred.stepPc    := 0.U // Unused, will be generated in realigner
    inflight.pred.take      := brTake
    inflight.pred.target    := pred.io.read.target
    inflight.predPcInMiddle := pred.io.read.pcInMiddle
  }

  // Resp
  val respEpoch = RegZero(UInt(p.EpochWidth.W))
  val respMatch = inflight.epoch === respEpoch

  respEpoch :=
    MuxIf(
      flush                    -> reqEpoch,
      (resp.fire && respMatch) -> (respEpoch +% 1.U),
    )(respEpoch)

  // Realign
  val (valid, ready, inst, isC, instPc, instPred) = if (p.ExtC) {
    val realigner = Module(new InstRealigner)
    realigner.io.input.valid     := resp.valid && respMatch
    realigner.io.input.bits.inst := resp.bits.data
    realigner.io.input.bits.req  := inflight
    realigner.io.flush           := flush
    realigner.io.output.ready    := !io.stall

    val decomp = Module(new RvcDecompressor)
    decomp.io.in := realigner.io.output.bits.inst

    (
      realigner.io.output.valid,
      realigner.io.input.ready,

      Mux(
        realigner.io.output.bits.isC,
        decomp.io.out,
        realigner.io.output.bits.inst,
      ),
      realigner.io.output.bits.isC,
      realigner.io.output.bits.pc,
      realigner.io.output.bits.pred,
    )
  } else (true.B, true.B, resp.bits.data, false.B, inflight.pc, inflight.pred)
  realignReady := ready

  resp.ready := ready || !respMatch

  // To Id
  toId.valid := valid
  toId.inst  := inst
  toId.isC   := isC
  toId.pc    := instPc
  toId.pred  := instPred

  toId.trap.valid := pc.end(p.PcUnusedWidth).orR
  toId.trap.cause := 0.U // Pc Misaligned

  io.busy := !valid
}
