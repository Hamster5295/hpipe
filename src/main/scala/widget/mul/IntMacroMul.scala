package hpipe.widget

import chisel3._
import chisel3.util._
import hammer._
import harith.sint.SIntMacroMul

class IntMacroMul(width: Int) extends Module with IntMulBase {
  val io = IO(new IntMulIO(width))

  val latency = 2

  val mul = Module(new SIntMacroMul(width + 1))
  mul.io.src1 := RegNext(Mux(io.aSigned, SignExt(io.a, 33), io.a)).asSInt
  mul.io.src2 := RegNext(Mux(io.bSigned, SignExt(io.b, 33), io.b)).asSInt

  io.o := RegNext(mul.io.output.asUInt).asUInt

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
