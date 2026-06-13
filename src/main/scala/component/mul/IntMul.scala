package hpipe

import chisel3._
import chisel3.util._

class IntMulIO(width: Int) extends Bundle {
  val valid = Input(Bool())
  val busy  = Output(Bool())

  val a       = Input(UInt(width.W))
  val b       = Input(UInt(width.W))
  val aSigned = Input(Bool())
  val bSigned = Input(Bool())
  val o       = Output(UInt((width * 2).W))
}

class IntMul(width: Int) extends Module {
  val io = IO(new IntMulIO(width))
}
