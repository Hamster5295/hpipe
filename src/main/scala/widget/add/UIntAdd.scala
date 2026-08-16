package hpipe

import chisel3._
import chisel3.util._
import hammer._

class UIntAddIO(width: Int) extends Bundle {
  val a = Input(UInt(width.W))
  val b = Input(UInt(width.W))
  val c = Input(Bool())
  val o = Output(UInt((width + 1).W))
}

abstract class AbstractUIntAdd(width: Int) extends Module {
  val io = IO(new UIntAddIO(width))
}

class UIntAdd(
    width:   Int,
    groupBy: Int = 4,
)(implicit p: HPipeParameters) extends AbstractUIntAdd(width) {
  val inner = Module(
    if (p.UseArithMacro) new UIntMacroAdd(width)
    else new UIntCla(width, groupBy),
  )

  io <> inner.io
}

object UIntAdd {

  def apply(width: Int, a: UInt, b: UInt)(implicit
      p: HPipeParameters,
  ): UInt =
    apply(width, a, b, 0.B)

  def apply(width: Int, a: UInt, b: UInt, c: Bool)(implicit
      p: HPipeParameters,
  ): UInt = {
    val adder = Module(new UIntAdd(width, 4))

    adder.io.a := a
    adder.io.b := b
    adder.io.c := c

    adder.io.o
  }
}