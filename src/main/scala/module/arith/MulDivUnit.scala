package hpipe

import chisel3._
import chisel3.util._
import hammer._
import hpipe.MulDivOp._

class MulDivUnitIO(implicit val p: HPipeParameters) extends Bundle {
  val src1   = Input(Word())
  val src2   = Input(Word())
  val op     = Input(UInt(3.W))
  val result = Output(Word())

  val valid = Input(Bool())
  val busy  = Output(Bool())
}

class MulDivUnit(implicit val p: HPipeParameters) extends Module {
  val io = IO(new MulDivUnitIO)

  val isMul      = ~io.op.msb()
  val isMulUpper = io.op.in(Mulh.asUInt, Mulhu.asUInt, Mulhsu.asUInt)
  val isDiv      = io.op.msb() && ~io.op(1)
  val isRem      = io.op.msb() && io.op(1)
  val isSigned   = io.op.in(Div.asUInt, Rem.asUInt)

  val mul = Module(new IntMul(32))
  mul.io.valid   := io.valid && isMul
  mul.io.clear   := ~io.valid
  mul.io.a       := io.src1
  mul.io.b       := io.src2
  mul.io.aSigned := io.op.in(Mul.asUInt, Mulh.asUInt, Mulhsu.asUInt)
  mul.io.bSigned := io.op.in(Mul.asUInt, Mulh.asUInt)

  val div = Module(new IntNonRestoringDiv(32))
  div.io.dividend := io.src1
  div.io.divisor  := io.src2
  div.io.signed   := isSigned
  div.io.valid    := io.valid && (isDiv || isRem)
  div.io.clear    := ~io.valid

  io.result := MuxIf(
    isRem      -> div.io.remainder,
    isDiv      -> div.io.quotient,
    isMulUpper -> mul.io.o.head(p.DataWidth),
  )(mul.io.o.end(p.DataWidth))
  io.busy := Mux(isMul, mul.io.busy, div.io.busy)
}
