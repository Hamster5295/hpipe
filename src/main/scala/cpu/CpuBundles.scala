package hpipe

import chisel3._
import chisel3.util._

// Data Bundles

class InstFetchIO(implicit val p: HPipeParameters) extends Bundle {
  val addr = Output(Addr())
  val inst = Input(Inst())
}

class MemLoadIO(implicit val p: HPipeParameters) extends Bundle {
  val req  = Output(Bool())
  val addr = Output(Addr())
  val data = Input(Word())
}

class MemStoreIO(implicit val p: HPipeParameters) extends Bundle {
  val req  = Output(Bool())
  val addr = Output(Addr())
  val data = Output(Word())
  val mask = Output(UInt(4.W))
}

class DestInfo(implicit val p: HPipeParameters) extends Bundle {
  val rd      = XRegAddr()
  val isWrite = Bool()
  val isLd    = Bool()
  val data    = Word()

  def isValid(rs: UInt) = isWrite && rd.orR && (rs === rd)
}

class OpFlags(implicit val p: HPipeParameters) extends Bundle {
  val writeRd  = Bool() // Write data back to rf
  val isBr     = Bool() // Branch current pc
  val isLd     = Bool() // Load data in mem stage
  val isSt     = Bool() // Store data in mem stage
  val isJal    = Bool() // Is JAL (get PC+4 and use it for wb)
  val isAluInv = Bool() // Is Invert op in ALU (for `sub` and `sra`)
  val isECall  = Bool() // Is ECall Inst
  val isEBreak = Bool() // Is EBreak Inst
  val isMulDiv = Bool() // Is Mul || Div
  val isCsr    = Bool()

  def isMem = isLd || isSt
}

class BranchInfo(implicit val p: HPipeParameters) extends Bundle {
  val flags = new BranchFlags()

  val pc       = Addr()
  val redirect = Bool()
  val target   = Addr()

  val brTake   = Bool()
  val callAddr = Addr()
}

class BranchFlags(implicit val p: HPipeParameters) extends Bundle {
  val isJalr = Bool()
  val isJal  = Bool()
  val isBr   = Bool()
  val isCall = Bool()
  val isRet  = Bool()
}

class BranchPredictInfo(implicit val p: HPipeParameters) extends Bundle {
  val flags = new BranchFlags

  val jalrSrc = XRegAddr()
  val brTake  = Bool()

  val defaultTarget = Addr()
  val target        = Addr()
}

class RetireInfo(implicit val p: HPipeParameters) extends Bundle {
  val valid  = Bool()
  val pc     = Addr()
  val ecall  = Bool()
  val ebreak = Bool()
}

class DebugRegFile(implicit p: HPipeParameters) extends Bundle {
  val zero = Word()
  val ra   = Word()
  val sp   = Word()
  val gp   = Word()
  val tp   = Word()
  val t0   = Word()
  val t1   = Word()
  val t2   = Word()
  val s0   = Word()
  val s1   = Word()
  val a0   = Word()
  val a1   = Word()
  val a2   = Word()
  val a3   = Word()
  val a4   = Word()
  val a5   = Word()
  val a6   = Word()
  val a7   = Word()
  val s2   = Word()
  val s3   = Word()
  val s4   = Word()
  val s5   = Word()
  val s6   = Word()
  val s7   = Word()
  val s8   = Word()
  val s9   = Word()
  val s10  = Word()
  val s11  = Word()
  val t3   = Word()
  val t4   = Word()
  val t5   = Word()
  val t6   = Word()
}

class DebugInfo(implicit val p: HPipeParameters) extends Bundle {
  val pcIf  = Addr()
  val pcId  = Addr()
  val pcEx  = Addr()
  val pcMem = Addr()
  val pcWb  = Addr()

  val regs    = Vec(31, Word())
  val regInfo = new DebugRegFile

  val csr = new Csr
}

// Pipeline IOs

class PipeIO(implicit val p: HPipeParameters) extends Bundle {
  val valid = Bool()
}

class StageIO(implicit val p: HPipeParameters) extends Bundle {
  val busy = Bool()
}

class If2IdIO(implicit p: HPipeParameters) extends PipeIO {
  val inst = Inst()
  val pc   = Addr()

  val prediction = new BranchPredictInfo
}

class Id2ExIO(implicit p: HPipeParameters) extends PipeIO {
  val pc = Addr()

  val rs1 = XRegAddr()
  val rs2 = XRegAddr()
  val rd  = XRegAddr()

  val src1 = Word()
  val src2 = Word()
  val addr = Addr() // Branch Address (if any)
  val csr  = CsrAddr()

  val funct = UInt(3.W)
  val flags = new OpFlags()

  val predInfo = new BranchPredictInfo()
}

class Ex2MemIO(implicit p: HPipeParameters) extends PipeIO {
  val pc = Addr()
  val rd = XRegAddr()

  val funct = UInt(3.W)
  val data  = Word()
  val addr  = Addr()
  val csr   = CsrAddr()

  val flags = new OpFlags()
}

class Mem2WbIO(implicit p: HPipeParameters) extends PipeIO {
  val pc      = Addr()
  val writeRd = Bool()
  val rd      = XRegAddr()
  val data    = Word()

  val isECall = Bool()
  val isEbreak = Bool()

  val isCsr = Bool()
  val csr   = CsrAddr()
  val csrOp = UInt(3.W)
}

// Enums

object InstType extends ChiselEnum {
  val Invalid, R, I, S, B, U, J, Csr, N = Value
}

object Src1 extends ChiselEnum {
  val Reg, PC, Imm, None = Value
}

object Src2 extends ChiselEnum {
  val Reg, Imm, Four, None = Value
}

object ALUOp extends ChiselEnum {
  val ADD, SLL, SLT, SLTU, XOR, SRX, OR, AND = Value
}

object MulDivOp extends ChiselEnum {
  val MUL, MULH, MULHSU, MULHU, DIV, DIVU, REM, REMU = Value
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
