package hpipe

import chisel3._
import chisel3.util._

class InstFetchIO(implicit val p: Parameters) extends Bundle {
  val addr = Output(Addr())
  val inst = Input(Inst())
}

class MemLoadIO(implicit val p: Parameters) extends Bundle {
  val req  = Output(Bool())
  val addr = Output(Addr())
  val data = Input(Word())
}

class MemStoreIO(implicit val p: Parameters) extends Bundle {
  val req  = Output(Bool())
  val addr = Output(Addr())
  val data = Output(Word())
  val mask = Output(UInt(4.W))
}

class FeedForward(implicit val p: Parameters) extends Bundle {
  val rd      = XRegAddr()
  val writeRd = Bool()
  val data    = Word()

  def isValid(rs: UInt) = writeRd && rd.orR && (rs === rd)
}

class If2IdIO(implicit val p: Parameters) extends Bundle {
  val inst = Output(Inst())
  val pc   = Output(Addr())
}

class Id2ExIO(implicit val p: Parameters) extends Bundle {
  val pc = Addr()

  val rs1 = XRegAddr()
  val rs2 = XRegAddr()
  val rd  = XRegAddr()

  val src1 = Word()
  val src2 = Word()
  val addr = Addr() // Branch Address (if any)

  val funct = UInt(3.W)
  val uop   = new UOp()
}

class Ex2MemIO(implicit val p: Parameters) extends Bundle {
  val pc = Addr()
  val rd = XRegAddr()

  val funct = UInt(3.W)
  val alu   = Word()
  val addr  = Addr()

  val uop = new UOp()
}

class Mem2WbIO(implicit val p: Parameters) extends Bundle {
  val pc      = Addr()
  val writeRd = Bool()
  val rd      = XRegAddr()
  val data    = Word()
}

class UOp(implicit val p: Parameters) extends Bundle {
  val writeRd = Bool() // Write data back to rf
  val isBr    = Bool() // Branch current pc
  val isLd    = Bool() // Load data in mem stage
  val isSt    = Bool() // Store data in mem stage
  val isJal   = Bool() // Is JAL (get PC+4 and use it for wb)
  val isSra   = Bool() // Is SRA (for ALU)

  def isMem = isLd || isSt
}

object InstType extends ChiselEnum {
  val Invalid, R, I, S, B, U, J, N = Value
}

object Src1 extends ChiselEnum {
  val Reg, PC, None = Value
}

object Src2 extends ChiselEnum {
  val Reg, Imm, Four, None = Value
}

object ALUOp extends ChiselEnum {
  val ADD, SLL, SLT, SLTU, XOR, SRX, OR, AND = Value
}

object LoadOp extends ChiselEnum {
  val Byte, Half, Word = Value
  val UByte            = Value(4.U)
  val UHalf            = Value
}

object StoreOp extends ChiselEnum {
  val Byte, Half, Word = Value
}

object BranchOp extends ChiselEnum {
  val EQ, NE       = Value
  val LT           = Value(4.U)
  val GE, LTU, GEU = Value
}
