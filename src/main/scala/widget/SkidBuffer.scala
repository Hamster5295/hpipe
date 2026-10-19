package hpipe

import chisel3._
import chisel3.util._
import hammer._

class SkidBufferIO[T <: Data](gen: => T) extends Bundle {
  val input  = Flipped(Decoupled(gen))
  val output = Decoupled(gen)
  val flush  = Input(Bool())
}

class SkidBuffer[T <: Data](gen: => T) extends Module {
  val io = IO(new SkidBufferIO(gen))

  val reg        = RegZero(Valid(gen))
  when(io.input.fire && !io.output.ready)(reg.valid        := true.B)
  when(io.output.ready && reg.valid || io.flush)(reg.valid := false.B)

  reg.bits := io.input.bits

  io.input.ready := !reg.valid

  io.output.valid := reg.valid || io.input.valid
  io.output.bits  := Mux(reg.valid, reg.bits, io.input.bits)
}

object SkidBuffer {

  def apply[T <: Data](next: DecoupledIO[T]): DecoupledIO[T] = {
    val skid = Module(new SkidBuffer(chiselTypeOf(next.bits)))
    skid.io.input <> next
    skid.io.flush := false.B
    skid.io.output
  }

  def apply[T <: Data](next: DecoupledIO[T], flush: Bool): DecoupledIO[T] = {
    val skid = Module(new SkidBuffer(chiselTypeOf(next.bits)))
    skid.io.input <> next
    skid.io.flush := flush
    skid.io.output
  }
}
