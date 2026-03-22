package hpipe

import chisel3._
import chisel3.util._
import hammer._

trait HasRetAddrStackParameter {
  implicit val p: Parameters
  val config = p.RetAddrStack
}

class RetAddrStackIO(implicit p: Parameters) extends BranchPredictorIO

class RetAddrStack(implicit val p: Parameters) extends Module
    with HasRetAddrStackParameter {
  val io = IO(new RetAddrStackIO)

  val stack = Reg(Vec(config.Depth, Addr()))
  val ptr   = RegZero(UInt(config.PtrWidth.W))

  val stackTop = ptr -% 1.U

  // Write
  val write = io.write.info.isCall
  stack(ptr) := io.write.callAddr

  // Read
  val read = io.read.info.isRet
  io.read.target := stack(stackTop)

  ptr := MuxIf(
    (read && write) -> ptr,
    read            -> (stackTop),
    write           -> (ptr +% 1.U),
  )(ptr)

  io.read.brTake := false.B
}
