package hpipe

import chisel3._
import chisel3.util._

class IntFpgaMul(width: Int) extends IntMul(width) {
    
  val a = Mux(io.aSigned, ~io.a + 1.U, io.a)
  val b = Mux(io.bSigned, ~io.b + 1.U, io.b)

  val res = RegNext(a * b)

  io.o := Mux(io.aSigned ^ io.bSigned, ~res + 1.U, res)
  io.busy := io.valid
}
