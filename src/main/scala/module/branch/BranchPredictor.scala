package hpipe

import chisel3._
import chisel3.util._
import hammer._
import hammer.model.Fixed

class BranchReadPortBase(implicit p: Parameters) extends Bundle {
  val pc     = Input(Addr())
  val brAddr = Input(Addr())

  val nextpc = Output(Addr())
  val take   = Output(Bool())
}

class BranchReadPort(implicit p: Parameters) extends BranchReadPortBase {
  val isJalr = Input(Bool())
  val isJal  = Input(Bool())
  val isBr   = Input(Bool())
}

class BranchWritePort(implicit p: Parameters) extends Bundle {
  val isBr = Input(Bool())
  val pc    = Input(Addr())
  val take  = Input(Bool())
}

class BranchPredictorIO(implicit p: Parameters) extends Bundle {
  val read  = new BranchReadPort
  val write = new BranchWritePort
}

class BranchPredictor(implicit p: Parameters) extends Module {
  val io = IO(new BranchPredictorIO)

  val defaultPc = io.read.pc +% 4.U

  val hist = Module(new HistBuffer)
  io.read :>> hist.io.read
  hist.io.write       := io.write
  hist.io.defaultPc   := defaultPc
}
