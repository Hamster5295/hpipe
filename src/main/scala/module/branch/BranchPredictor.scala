package hpipe

import chisel3._
import chisel3.util._
import hammer._

class BranchPort(implicit p: HPipeParameters) extends Bundle {
  val pc    = Input(Addr())
  val flags = Input(new BranchFlags)
}

class BranchReadPort(implicit p: HPipeParameters) extends BranchPort {
  val target = Output(Addr())
  val take   = Output(Bool())

  val jalAddr = Input(Addr())
}

class BranchWritePort(implicit p: HPipeParameters) extends BranchPort {
  val valid  = Input(Bool())
  val target = Input(Addr())
  val take   = Input(Bool())
}

class BranchPredictorIO(implicit p: HPipeParameters) extends Bundle {
  val read  = new BranchReadPort
  val write = new BranchWritePort
}

class BranchPredictor(implicit p: HPipeParameters) extends Module {
  val io = IO(new BranchPredictorIO)

  val btb = Module(new TargetBuffer)
  btb.io.pc          := io.read.pc
  btb.io.writeEnable := io.write.valid && !io.write.flags.isStack
  btb.io.writePc     := io.write.pc
  btb.io.writeData   := io.write.target

  val bht = Module(new HistoryTable)
  bht.io.pc          := io.read.pc
  bht.io.writeEnable := io.write.valid && !io.write.flags.isStack
  bht.io.writePc     := io.write.pc
  bht.io.writeTake   := io.write.take

  val ras = Module(new RetAddrStack)
  ras.io.flags       := io.read.flags
  ras.io.writeTarget := io.write.target

  io.read.take :=
    btb.io.hit && bht.io.take || io.read.flags.isStack || io.read.flags.isJal
  io.read.target := MuxIf(
    io.read.flags.isJal   -> io.read.jalAddr,
    io.read.flags.isStack -> ras.io.target,
  )(btb.io.target)
}
