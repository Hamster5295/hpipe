package hpipe

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._
import hammer._
import hpipe.branch._
import hpipe.decode._
import hpipe.decode.Insts._

// class PipeIfIO(implicit p: HPipeParameters) extends StageIO {
//   val inst = new CoreReadPort

//   val fromIr = Input(new Ir2IfIO)
//   val toId   = Output(new If2IdIO)

//   val isC = Output(Bool())

//   val fromEx = Input(new BranchInfo)

//   val feedForwardMem = Input(new DestInfo)
//   val feedForwardEx  = Input(new DestInfo)
//   val feedForwardSg  = Input(new DestInfo)
//   val feedForwardId  = Input(new DestInfo)

//   val stall = Input(Bool())
//   val trap  = Input(Bool())

//   val csr = Input(new Csr)
// }

class PipeIfIO(implicit p: HPipeParameters) extends StageIO {
  val inst = Flipped(Decoupled(new ReadResp(p.XLEN)))

  val fromIr = Input(new Ir2IfIO)
  val toId   = Output(new If2IdIO)

  val isC = Output(Valid(Bool()))

  val stall = Input(Bool())
  val trap  = Input(Bool())
}

class PipeIf(implicit val p: HPipeParameters)
    extends StageModule(new PipeIfIO) {

  // State
  val sIdle :: sFlush :: Nil = Enum(2)
  val state                  = RegInit(sIdle)
  val isIdle                 = state === sIdle
  val isFlush                = state === sFlush

  val fromIr = io.fromIr

  // Resp
  val resp = io.inst
  resp.ready := !io.stall
  val respFire = resp.fire && !isFlush

  val rawInst = resp.bits.data

  io.isC.valid := fromIr.valid && respFire && p.ExtC.B
  io.isC.bits  := !rawInst.end(2).andR // The first cycle is always not C
  val isC = io.isC.valid && io.isC.bits

  val inst = if (p.ExtC) {
    val decomp = Module(new RvcDecompressor)
    decomp.io.in := rawInst
    Mux(isC, decomp.io.out, rawInst)
  } else rawInst

  val toId = io.toId
  toId.valid := fromIr.valid && respFire
  toId.pc    := fromIr.pc
  toId.inst  := inst
  toId.isC   := isC
  toId.pred  := fromIr.pred
  toId.trap  := fromIr.trap

  when(!fromIr.trap.valid && resp.bits.excp) {
    toId.trap.valid := true.B
    toId.trap.cause := 1.U // Inst Fetch Error
  }

  val busy = fromIr.valid && !resp.valid
  io.busy := busy

  // State Machine
  switch(state) {
    is(sIdle) {
      // Case: When Ir requested, not responsed, but a flush is coming
      //       The input regs are flushed, but the request is not
      //       So we need to flush the incoming response once
      when(io.trap && busy)(state := sFlush)
    }

    is(sFlush) {
      when(resp.fire)(state := sIdle)
    }
  }
}
