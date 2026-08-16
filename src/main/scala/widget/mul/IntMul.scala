package hpipe

import chisel3._
import chisel3.util._

class IntMulIO(width: Int) extends Bundle {
  val valid = Input(Bool())
  val busy  = Output(Bool())
  val clear = Input(Bool())

  val a       = Input(UInt(width.W))
  val b       = Input(UInt(width.W))
  val aSigned = Input(Bool())
  val bSigned = Input(Bool())
  val o       = Output(UInt((width * 2).W))
}

abstract class AbstractIntMul(width: Int) extends Module {
  val io = IO(new IntMulIO(width))
}

class IntMul(width: Int)(implicit p: HPipeParameters)
    extends AbstractIntMul(width) {
  val inner = Module(
    if (p.UseArithMacro) new IntMacroMul(width)
    else new IntBoothMul(width),
  )

  io <> inner.io
}