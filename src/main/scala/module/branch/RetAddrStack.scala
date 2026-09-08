package hpipe

import chisel3._
import chisel3.util._
import hammer._

trait HasRetAddrStackParameter {
  implicit val p: HPipeParameters
  val config = p.RetAddrStack
}

class RetAddrStackIO(implicit p: HPipeParameters) extends Bundle {
  val target = Valid(Addr())

  val writeEnable = Input(Bool())
  val flags       = Input(new BranchFlags)
  val writeTarget = Input(Addr())
}

class RetAddrStack(implicit val p: HPipeParameters) extends Module
    with HasRetAddrStackParameter {
  val io = IO(new RetAddrStackIO)

  val stack = Reg(Vec(config.Depth, Addr()))
  val ptr   = RegZero(UInt(config.PtrWidth.W))

  def isEmpty = ptr === 0.U
  def isFull  = ptr === config.Depth.U

  val stackTop = ptr -% 1.U
  io.target.valid := ptr =/= 0.U
  io.target.bits  := stack(stackTop)

  // Write
  val push = io.flags.isCall
  val pop  = io.flags.isRet
  stack(ptr) := io.writeTarget

  ptr := MuxIf(
    !io.writeEnable   -> ptr,
    (push && !isFull) -> (ptr +% 1.U),
    (pop && !isEmpty) -> (ptr -% 1.U),
  )(ptr)
}
