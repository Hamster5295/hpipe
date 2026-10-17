package hpipe

import chisel3._
import chisel3.util._
import hammer._
import hpipe.branch.BranchPredictor

class PipeIrIO(implicit p: HPipeParameters) extends StageIO {
  val inst = Decoupled(new ReadReq(p.XLEN))

  val toIf   = Output(new Ir2IfIO)
  val isC    = Flipped(Valid(Bool()))
  val branch = Flipped(Valid(new BranchInfo))

  val stall = Input(Bool())
  val trap  = Input(Bool())

  val csr = Input(new Csr)
}

class PipeIr(implicit p: HPipeParameters) extends StageModule(new PipeIrIO) {

  val inst   = io.inst
  val branch = io.branch

  // State
  val sIdle :: sWfi :: Nil = Enum(2)
  val state                = RegInit(sIdle)
  val isIdle               = state === sIdle
  val isWfi                = state === sWfi

  val stall = io.stall || isWfi

  // PC Update
  val wasC = RegInit(false.B)
  val isC  = Mux(io.isC.valid, io.isC.bits, wasC)
  wasC := Mux(io.branch.bits.redirect, false.B, isC)

  val pc    = RegInit(UInt(p.XLEN.W), p.ResetVector.U)
  val reqPc = Mux(
    isC && !branch.bits.redirect,
    pc -% 2.U,
    pc,
  ) // First cycle always predicts !isC. Fix it if first is C.

  val pred = Module(new BranchPredictor)
  pred.io.read.pc := reqPc
  pred.io.write   := branch

  val stepPc = pc +% Mux(isC, 2.U, 4.U)
  val nextPc = Mux(pred.io.read.take, pred.io.read.target, stepPc)

  pc := MuxIf(
    io.trap               -> io.csr.mtvec,
    branch.bits.redirect  -> branch.bits.target,
    (stall || !inst.fire) -> pc,
  )(nextPc)

  // Request
  inst.valid     := isIdle && !io.stall && !io.trap
  inst.bits.addr := reqPc

  // To If
  val toIf = io.toIf
  toIf.valid := isIdle && inst.fire
  toIf.pc    := reqPc
  toIf.inst  := 0.U // Oh st, improper abstraction

  io.busy := !inst.ready

  // For now, branch prediction is disabled as it's not migrated to pure pc query
  toIf.pred.stepPc := stepPc
  toIf.pred.take   := pred.io.read.take
  toIf.pred.target := pred.io.read.target
//   toIf.pred.flags  := 0.U.asTypeOf(new BranchFlags)

  toIf.trap.valid := reqPc.end(p.PcUnusedWidth).orR
  toIf.trap.cause := 0.U // Pc Misaligned
}
