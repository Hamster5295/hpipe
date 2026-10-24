package hpipe.widget

import chisel3._
import chisel3.util._
import hammer._
import harith.uint._
import hpipe._

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

class IntMul(width: Int)(implicit p: HPipeParameters) extends Module {
  val io = IO(new IntMulIO(width))

  val mul = Module(if (p.UseArithMacro) new IntMacroMul(width)
  else new IntAsicMul(width))

  io <> mul.io
}

trait IntMulBase {
  val io: IntMulIO
}
