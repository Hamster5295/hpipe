package hpipe

import chisel3._
import chisel3.util._
import hammer._

class IntDiv(width: Int) extends Bundle {
  val valid    = Input(Bool())
  val dividend = Input(UInt(width.W))
  val divisor  = Input(UInt(width.W))
  val signed   = Input(Bool())
  val clear    = Input(Bool())

  val busy      = Output(Bool())
  val quotient  = Output(UInt(width.W))
  val remainder = Output(UInt(width.W))
}

class IntNonRestoringDiv(width: Int) extends Module {
  val io = IO(new IntDiv(width))

  val reg = RegZero(UInt((width * 2 + 1).W))

  val busy  = RegInit(false.B)
  val timer = RegZero(UInt(log2Ceil(width).W))

  val fire = !busy && io.valid

  val dividedByZero = !io.divisor.orR
  val overflow      = io.dividend.msb() && io.divisor.andR && io.signed
  val exception     = fire && (dividedByZero || overflow)

  val ending = timer === 1.U || exception
  val ended  = RegNext(RegNext(ending))

  timer := MuxIf(
    fire                -> (width - 1).U,
    (io.clear || ended) -> 0.U,
  )(timer - 1.U)

  busy := MuxIf(
    (io.clear || exception) -> false.B,
    fire                    -> true.B,
    ending                  -> false.B,
  )(busy)

  val dividendAbs = Mux(
    io.signed && io.dividend.msb(),
    ~io.dividend +% 1.U,
    io.dividend,
  )
  val divisorAbs = Mux(
    io.signed && io.divisor.msb(),
    ~io.divisor +% 1.U,
    io.divisor,
  )

  val dividend = Mux(fire, dividendAbs, reg) << 1
  val divisor  = 0.U(1.W) ## divisorAbs ## 0.U(width.W)

  val resSign = dividend.msb()
  val res     =
    UIntCLA(width * 2 + 1, 4)(
      dividend,
      Mux(~resSign, ~divisor, divisor),
      ~resSign,
    ).tail(1)

  reg := res.head(width * 2) ## ~res.msb()

  val sign = io.signed && (io.dividend.msb() ^ io.divisor.msb())
  val quo  = reg.end(width)
  val rem  = Mux(
    reg.msb(),
    UIntCLA(width, 4)(reg(width * 2 - 1, width), divisorAbs, 0.B),
    reg(width * 2 - 1, width),
  )

  io.busy     := (busy || fire) && !ended && RegNext(!exception)
  io.quotient := RegNext(MuxIf(
    overflow      -> io.dividend,
    dividedByZero -> Fill(width, 1.U(1.W)),
    sign          -> (~quo +% 1.U),
  )(quo))
  io.remainder := RegNext(MuxIf(
    overflow                         -> 0.U,
    dividedByZero                    -> io.dividend,
    (io.signed && io.dividend.msb()) -> (~rem +% 1.U),
  )(rem))
}
