package hpipe

import chisel3._
import chisel3.util._

// Data Bundles

class InstFetchIO(implicit val p: HPipeParameters) extends Bundle {
  val addr = Output(Addr())
  val inst = Input(Inst())
}

class MemLoadReq(implicit p: HPipeParameters) extends Bundle {
  val valid = Bool()
  val addr  = Addr()
}

class MemStoreReq(implicit p: HPipeParameters) extends Bundle {
  val valid = Bool()
  val addr  = Addr()
  val data  = Word()
  val mask  = Mask()
}

class MemLoadIO(implicit val p: HPipeParameters) extends Bundle {
  val req  = Output(new MemLoadReq)
  val data = Input(Word())
}

class MemStoreIO(implicit val p: HPipeParameters) extends Bundle {
  val req = Output(new MemStoreReq)
}

class GprDestInfo(implicit p: HPipeParameters) extends Bundle {
  val addr = XRegAddr()
  val data = Word()
  val isLd = Bool()
}

class CsrDestInfo(implicit p: HPipeParameters) extends Bundle {
  val addr = CsrAddr()
  val data = Word()
}

class DestInfo(implicit val p: HPipeParameters) extends Bundle {
  val gpr = Valid(new GprDestInfo)
  val csr = Valid(new CsrDestInfo)

  def gprMatch(addr: UInt) = gpr.valid && gpr.bits.addr === addr
  def csrMatch(addr: UInt) = csr.valid && csr.bits.addr === addr
}

class OpFlags(implicit val p: HPipeParameters) extends Bundle {
  val writeRd = Bool() // Write data back to rf
  val br      = Bool() // Branch current pc
  val ld      = Bool() // Load data in mem stage
  val st      = Bool() // Store data in mem stage
  val jal     = Bool() // Is JAL (get PC+4 and use it for wb)
  val aluInv  = Bool() // Is Invert op in ALU (for `sub` and `sra`)
  val ecall   = Bool() // Is ECall Inst
  val ebreak  = Bool() // Is EBreak Inst
  val mret    = Bool() // Is MRet Inst

  val muldiv = Bool() // Is Mul || Div
  val csr    = Bool()

  def isMem = ld || st
}

class BranchInfo(implicit val p: HPipeParameters) extends Bundle {
  val valid = Bool()

  val pc    = Addr()
  val flags = new BranchFlags()

  val take   = Bool()
  val target = Addr()

  val redirect       = Bool()
  val redirectTarget = Addr()
}

class BranchFlags(implicit val p: HPipeParameters) extends Bundle {
  val isJal  = Bool()
  val isCall = Bool()
  val isRet  = Bool()

  def isStack = isCall || isRet
}

class BranchPredictInfo(implicit val p: HPipeParameters) extends Bundle {
  val flags = new BranchFlags

  val take   = Bool()
  val target = Addr()
  val stepPc = Addr()
}

class RetireInfo(implicit val p: HPipeParameters) extends Bundle {
  val valid     = Bool()
  val pc        = Addr()
  val trap = new TrapInfo

  val ebreak = Bool()

  def trapValid = valid && trap.valid
}

class TrapInfo(implicit p: HPipeParameters) extends Bundle {
  val valid = Bool()
  val cause = Word()
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
  val pcSg  = Addr()
  val pcEx  = Addr()
  val pcMem = Addr()
  val pcWb  = Addr()

  val regs    = Vec(31, Word())
  val regInfo = new DebugRegFile

  val branch     = Bool()
  val branchMiss = Bool()

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

  val src1    = Word()
  val src2    = Word()
  val addr    = Addr() // Branch Address (if any)
  val csrAddr = CsrAddr()
  val csrSrc  = Word()

  val funct = UInt(3.W)
  val flags = new OpFlags()

  val exception = new TrapInfo
  val predInfo  = new BranchPredictInfo
}

class Id2SgIO(implicit p: HPipeParameters) extends PipeIO {
  val pc = Addr()

  val rs1Addr = XRegAddr()
  val rs2Addr = XRegAddr()
  val rdAddr  = XRegAddr()
  val csrAddr = CsrAddr()

  val decoded = new DecodeResult

  val trap = new TrapInfo
  val pred = new BranchPredictInfo
}

class Sg2ExIO(implicit p: HPipeParameters) extends PipeIO {
  val pc = Addr()

  val rs1Addr = XRegAddr()
  val rs2Addr = XRegAddr()
  val rdAddr  = XRegAddr()
  val csrAddr = CsrAddr()
//   val addr    = Addr() // Branch Address (if any)

  val src1   = Word()
  val src2   = Word()
  val csrSrc = Word()

  val addrBase = Addr()
  val imm      = Word()

  val funct = UInt(3.W)
  val flags = new OpFlags()

  val trap = new TrapInfo
  val pred = new BranchPredictInfo
}

class Ex2MemIO(implicit p: HPipeParameters) extends PipeIO {
  val pc = Addr()
  val rd = XRegAddr()

  val funct   = UInt(3.W)
  val data    = Word()
  val addr    = Addr()
  val csrAddr = CsrAddr()
  val csrData = Word()

  val flags = new OpFlags()
  val trap  = new TrapInfo
}

class Mem2WbIO(implicit p: HPipeParameters) extends PipeIO {
  val pc   = Addr()
  val rd   = XRegAddr()
  val data = Word()

  val csrAddr = CsrAddr()
  val csrData = Word()

  val flags     = new OpFlags()
  val trap = new TrapInfo
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
  val Add, Sll, Slt, Sltu, Xor, Srx, Or, And = Value
}

object MulDivOp extends ChiselEnum {
  val Mul, Mulh, Mulhsu, Mulhu, Div, Divu, Rem, Remu = Value
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
