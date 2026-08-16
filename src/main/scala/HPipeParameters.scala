package hpipe

import chisel3._
import chisel3.util.log2Ceil
import hammer._
import scala.annotation.meta.param

case class HPipeParameters(
    val Debug:         Boolean = false,
    val UseArithMacro: Boolean = false,

    val XLEN: Int = 32,

    val InstWidth: Int = 32,
    val DataWidth: Int = 32,

    val ResetVector: String = "x80000000",

    // Supported ISA Extensions
    val ExtC: Boolean = false,

    // Branch
    val Branch:       Boolean = true,
    val TargetBuf:    TargetBufferParameters = TargetBufferParameters(),
    val HistTable:    HistoryTableParameters = HistoryTableParameters(),
    val RetAddrStack: RetAddrStackParameters = RetAddrStackParameters(),
) {
  val XRegAddrWidth = log2Ceil(XLEN)
  val AddrWidth     = DataWidth
}

case class TargetBufferParameters(
    val Size:     Int = 32,
    val Ways:     Int = 2,
    val TagWidth: Int = 16,
)

case class HistoryTableParameters(
    val Size:        Int = 256,
    val RecordWidth: Int = 2,
)

case class RetAddrStackParameters(
    val Depth:    Int = 8,
    val TagWidth: Int = 16,
) {
  val PtrWidth = log2Ceil(Depth)
}

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
  def apply() = UInt(12.W) // 12 is specified by

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
