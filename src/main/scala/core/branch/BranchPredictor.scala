package hpipe.branch

import chisel3._
import chisel3.util._
import hammer._
import hpipe._

class BranchReadPort(implicit p: HPipeParameters) extends Bundle {
  val pc         = Input(Addr())
  val target     = Output(Addr())
  val take       = Output(Bool())
  val pcInMiddle = Output(Bool())
}

class BranchPredictorIO(implicit p: HPipeParameters) extends Bundle {
  val read  = new BranchReadPort
  val write = Flipped(Valid(new BranchInfo))
}

class BranchPredictor(implicit p: HPipeParameters) extends Module {
  val io = IO(new BranchPredictorIO)

  val (btb, bht, ras) =
    if (p.Branch.Enabled) {
      val btb = Module(new TargetBuffer)
      btb.io.pc          := io.read.pc
      btb.io.writeEnable := io.write.valid
      btb.io.writePc     := io.write.bits.pc
      btb.io.writeTarget := io.write.bits.target
      btb.io.writeFlags  := io.write.bits.flags

      val flags = btb.io.meta.flags

      val bht = Module(new HistoryTable)
      bht.io.pc          := io.read.pc
      bht.io.writeEnable := io.write.valid && !io.write.bits.flags.isStack
      bht.io.writePc     := io.write.bits.pc
      bht.io.writeTake   := io.write.bits.take

      val ras = Module(new RetAddrStack)
      ras.io.flags       := flags
      ras.io.writeEnable := io.write.valid
      ras.io.writeTarget := io.write.bits.stepPc

      io.read.take :=
        btb.io.hit &&
          (bht.io.take ||
            flags.isRet && ras.io.target.valid ||
            flags.isUncond)

      io.read.target := MuxIf(
        flags.isRet -> ras.io.target.bits,
      )(btb.io.target)

      io.read.pcInMiddle := btb.io.meta.pcInMiddle

      (btb, bht, ras)
    } else {
      io.read.take   := false.B
      io.read.target := 0.U
      (null, null, null)
    }
}
