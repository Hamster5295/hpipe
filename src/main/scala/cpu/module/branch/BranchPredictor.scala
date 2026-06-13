package hpipe

import chisel3._
import chisel3.util._
import hammer._
import hammer.model.Fixed
import os.Source.WritableSource

class BranchReadPort(implicit val p: HPipeParameters) extends Bundle {
  val pc            = Input(Addr())
  val defaultTarget = Input(Addr())
  val brAddr        = Input(Addr())

  val info     = Input(new BranchInfo)
  val rs1Valid = Input(Bool())

  val target = Output(Addr())
  val brTake = Output(Bool())
}

class BranchWritePort(implicit val p: HPipeParameters) extends Bundle {
  val info = Input(new BranchInfo)

  val pc       = Input(Addr())
  val brTake   = Input(Bool())
  val callAddr = Input(Addr())
}

class BranchPredictorIO(implicit val p: HPipeParameters) extends Bundle {
  val read  = new BranchReadPort
  val write = new BranchWritePort
}

class BranchPredictor(implicit val p: HPipeParameters) extends Module {
  val io = IO(new BranchPredictorIO)

  val hist = Module(new HistBuffer)
  io.read <> hist.io.read
  hist.io.write := io.write

  val ras = Module(new RetAddrStack)
  io.read <> ras.io.read
  ras.io.write := io.write

  val info = io.read.info
  io.read.target := MuxIf(
    ((info.isJalr && io.read.rs1Valid) || info.isJal) -> io.read.brAddr,
    info.isRet                                        -> ras.io.read.target,
    info.isBr                                         -> hist.io.read.target,
  )(io.read.defaultTarget)
  io.read.brTake := hist.io.read.brTake
}
