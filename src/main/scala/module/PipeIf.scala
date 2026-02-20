package hpipe

import chisel3._
import chisel3.util._
import hammer.MuxIf

class PipeIfIO(implicit p: Parameters) extends Bundle {
  val fetch = new InstFetchIO
  val toId  = new If2IdIO

  val fromEx = Input(ValidIO(Addr()))

  val stall = Input(Bool())
}

class PipeIf(implicit p: Parameters) extends Module {
  val io = IO(new PipeIfIO)

  val pc     = RegInit(UInt(p.AddrWidth.W), p.ResetVector.U)
  val nextpc = MuxIf(
    io.stall        -> pc,
    io.fromEx.valid -> io.fromEx.bits
  )(pc + 4.U)

  pc := nextpc

  io.fetch.addr := pc
  io.toId.valid := !reset.asBool
  io.toId.pc    := pc
  io.toId.inst  := io.fetch.inst
}
