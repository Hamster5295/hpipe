package hpipe.widget

import chisel3._
import chisel3.util._
import hammer._
import harith.sint.SIntPipelinedTreeMul
import harith.uint._

class IntAsicMul(width: Int) extends Module with IntMulBase {
  val io = IO(new IntMulIO(width))

  val latency = 2

  val mul = Module(new SIntPipelinedTreeMul(
    width + 1,
    ReductionStyle.Wallace,
    w => new UIntPrefixAdd(w, PrefixStyle.Sklansky),
    latency,
  ))
  mul.io.src1 := Mux(io.aSigned, SignExt(io.a, 33), io.a).asSInt
  mul.io.src2 := Mux(io.bSigned, SignExt(io.b, 33), io.b).asSInt

  io.o := mul.io.output.asUInt

  // Edge detection
  val lastValid = RegZero(Bool())
  lastValid := io.valid
  val fire = !lastValid && io.valid

  val cnter = RegZero(UInt(log2Ceil(latency).W))
  cnter := MuxIf(
    io.clear   -> 0.U,
    fire       -> latency.U,
    !cnter.orR -> 0.U,
  )(cnter - 1.U)

  io.busy := cnter.orR || fire
}
