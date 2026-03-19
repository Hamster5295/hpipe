package hpipe

import chisel3._
import chisel3.util._
import hammer._
import hpipe.MulDivOp._

class MulDivUnitIO(implicit p: Parameters) extends Bundle {
  val src1   = Input(Word())
  val src2   = Input(Word())
  val op     = Input(UInt(3.W))
  val result = Output(Word())

  val valid = Input(Bool())
  val busy  = Output(Bool())
}

class MulDivUnit(implicit p: Parameters) extends Module {
  val io = IO(new MulDivUnitIO)

  val isMul      = ~io.op.msb()
  val isMulUpper = io.op.in(MULH.asUInt, MULHU.asUInt, MULHSU.asUInt)
  val isDiv      = io.op.msb() && ~io.op(1)
  val isRem      = io.op.msb() && io.op(1)
  val isSigned   = io.op.in(DIV.asUInt, REM.asUInt)

  val mul = Module(new IntBoothMul(32))
  mul.io.a       := io.src1
  mul.io.b       := io.src2
  mul.io.aSigned := io.op.in(MUL.asUInt, MULH.asUInt, MULHSU.asUInt)
  mul.io.bSigned := io.op.in(MUL.asUInt, MULH.asUInt)

  val div = Module(new NonRestoringDiv(32))
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
  io.busy := Mux(isMul, false.B, div.io.busy)
}
