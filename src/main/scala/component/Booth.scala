package hpipe

import chisel3._
import chisel3.util._

class Booth4(width: Int) extends Module {
  val io = IO(new Bundle {
    val mul = Input(UInt(width.W))
    val enc = Input(UInt(3.W))
    val out = Output(UInt((width + 1).W))
  })

  val pos = io.mul.head(1) ## io.mul
  val neg = ~pos +% 1.U

  io.out := MuxCase(
    0.U,
    Seq(
      (io.enc === "b000".U) -> 0.U,
      (io.enc === "b001".U) -> pos,
      (io.enc === "b010".U) -> pos,
      (io.enc === "b011".U) -> (pos << 1.U),
      (io.enc === "b100".U) -> (neg << 1.U),
      (io.enc === "b101".U) -> neg,
      (io.enc === "b110".U) -> neg,
      (io.enc === "b111".U) -> 0.U
    )
  )
}

object Booth4 {
  def apply(mul: UInt, enc: UInt): UInt = {
    val module = Module(new Booth4(mul.getWidth))
    module.io.mul := mul
    module.io.enc := enc
    module.io.out
  }
}
