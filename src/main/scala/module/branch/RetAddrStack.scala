package hpipe

import chisel3._
import chisel3.util._
import hammer._

trait HasRetAddrStackParameter {
  implicit val p: HPipeParameters
  val config = p.RetAddrStack
}

class RetAddrStackIO(implicit p: HPipeParameters) extends Bundle {
  val target = Output(Addr())

  val writeEnable = Input(Bool())
  val flags       = Input(new BranchFlags)
  val writeTarget = Input(Addr())
}

class RetAddrStack(implicit val p: HPipeParameters) extends Module
    with HasRetAddrStackParameter {
  val io = IO(new RetAddrStackIO)

  val stack = Reg(Vec(config.Depth, Addr()))
  val ptr   = RegZero(UInt(config.PtrWidth.W))

  val stackTop = ptr -% 1.U
  io.target := stack(stackTop)

  // Write
  val push = io.flags.isCall
  val pop  = io.flags.isRet
  stack(ptr) := io.writeTarget

  ptr := MuxIf(
    !io.writeEnable -> ptr,
    push            -> (ptr +% 1.U),
    pop             -> (ptr -% 1.U),
  )(ptr)
}
