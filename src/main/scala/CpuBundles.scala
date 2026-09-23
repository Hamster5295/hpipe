package hpipe

import chisel3._
import chisel3.util._
import hpipe.decode._

// Data Bundles

class InstFetchPort(implicit val p: HPipeParameters) extends Bundle {
  val addr = Decoupled(Addr())
  val inst = Flipped(Decoupled(Inst()))
}

class MemWriteReq(implicit p: HPipeParameters) extends Bundle {
  val addr = Addr()
  val data = Word()
  val mask = Mask()
}

class MemReadPort(implicit val p: HPipeParameters) extends Bundle {
  val addr = Decoupled(Addr())
  val data = Flipped(Decoupled(Word()))
}

class MemWritePort(implicit val p: HPipeParameters) extends Bundle {
  val req = Decoupled(new MemWriteReq)
}

class InterruptSource(implicit p: HPipeParameters) extends Bundle {
  val external = Bool()
  val timer    = Bool()
  val software = Bool()
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
  val branch  = Bool() // Branch current pc
  val load    = Bool() // Load data in mem stage
  val store   = Bool() // Store data in mem stage
  val jal     = Bool() // Is JAL (get PC+4 and use it for wb)
  val aluInv  = Bool() // Is Invert op in ALU (for `sub` and `sra`)
  val ecall   = Bool() // Is ECall Inst
  val ebreak  = Bool() // Is EBreak Inst
  val mret    = Bool() // Is MRet Inst

  val muldiv = Bool() // Is Mul || Div
  val csr    = Bool() // Is Zicsr

  def isMem = load || store
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
  val isUncond = Bool()
  val isCall   = Bool()
  val isRet    = Bool()

  def isStack = isCall || isRet
}

class BranchPredictInfo(implicit val p: HPipeParameters) extends Bundle {
  val flags = new BranchFlags

  val take   = Bool()
  val target = Addr()
  val stepPc = Addr()
}

class RetireInfo(implicit val p: HPipeParameters) extends Bundle {
  val valid = Bool()
  val pc    = Addr()
  val inst  = Inst()
  val trap  = new TrapInfo

  val ebreak = Bool()

  def trapValid = valid && trap.valid
}

class TrapInfo(implicit p: HPipeParameters) extends Bundle {
  val valid = Bool()
  val cause = Word()
}

// Pipeline IOs

class PipeIO(implicit val p: HPipeParameters) extends Bundle {
  val valid = Bool()

  val pc   = Addr()
  val inst = Inst()
}

class StageIO(implicit val p: HPipeParameters) extends Bundle {
  val busy = Bool()
}

class StageModule[T <: StageIO](gen: => T)(implicit p: HPipeParameters)
    extends Module {
  val io = IO(gen)
}

class If2IdIO(implicit p: HPipeParameters) extends PipeIO {
  val pred = new BranchPredictInfo
  val isC  = Bool()
}

class Id2SgIO(implicit p: HPipeParameters) extends PipeIO {

  val rs1Addr = XRegAddr()
  val rs2Addr = XRegAddr()
  val rdAddr  = XRegAddr()
  val csrAddr = CsrAddr()

  val decoded = new DecodeResult
  val isC     = Bool()

  val trap = new TrapInfo
  val pred = new BranchPredictInfo
}

class Sg2ExIO(implicit p: HPipeParameters) extends PipeIO {
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
  val rd   = XRegAddr()
  val data = Word()

  val csrAddr = CsrAddr()
  val csrData = Word()

  val flags = new OpFlags()
  val trap  = new TrapInfo
}

// Enums

object Src1 extends ChiselEnum {
  val Reg, PC, Imm, None = Value
}

object Src2 extends ChiselEnum {
  val Reg, Imm, PcStep, None = Value
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

// Data Alias
object Addr {
  def apply()(implicit p: HPipeParameters) = UInt(p.AddrWidth.W)
}

object Inst {
  def apply()(implicit p: HPipeParameters) = UInt(p.InstWidth.W)
}

object Word {
  def apply()(implicit p: HPipeParameters) = UInt(p.DataWidth.W)
}

object Mask {
  def apply()(implicit p: HPipeParameters) = UInt((p.DataWidth / 4).W)
}

object XRegAddr {
  def apply()(implicit p: HPipeParameters) = UInt(p.XRegAddrWidth.W)
}

object CsrAddr {
  def apply() = UInt(12.W) // 12 is specified by Manual

  val MSTATUS = "x300".U
  val MIE     = "x304".U
  val MTVEC   = "x305".U
  val MEPC    = "x341".U
  val MCAUSE  = "x342".U
  val MTVAL   = "x343".U
  val MIP     = "x344".U

  val CYCLE    = "xC00".U
  val INSTRET  = "xC02".U
  val CYCLEH   = "xC80".U
  val INSTRETH = "xC82".U
}
