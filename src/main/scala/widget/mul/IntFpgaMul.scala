package hpipe

import chisel3._
import chisel3.util._
import hammer._

class IntFpgaMul(width: Int) extends IntMul(width) {

  val a = RegNext(Mux(io.aSigned, ~io.a + 1.U, io.a))
  val b = RegNext(Mux(io.bSigned, ~io.b + 1.U, io.b))

  val res = RegNext(a * b)

  io.o := RegNext(Mux(io.aSigned ^ io.bSigned, ~res + 1.U, res))

  val cnter = RegZero(UInt(2.W))
  io.busy := cnter.orR

  cnter := MuxIf(
    io.clear -> 0.U,
    io.valid -> 3.U,
  )(cnter - 1.U)
}
