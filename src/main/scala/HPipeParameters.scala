package hpipe

import chisel3._
import chisel3.util.log2Ceil
import chisel3.util.log2Up
import hammer._

case class HPipeParameters(
    val Debug: Boolean = false,

    val XLEN: Int = 32,

    val InstWidth: Int = 32,
    val DataWidth: Int = 32,

    val ResetVector: String = "x80000000",

    // Branch
    val HistBuf:      HistBufferParameters = HistBufferParameters(),
    val RetAddrStack: RetAddrStackParameters = new RetAddrStackParameters,
) {
  val XRegAddrWidth = log2Ceil(XLEN)
  val AddrWidth     = DataWidth
}

case class HistBufferParameters(
    val PCLen:      Int = 16,
    val Count:      Int = 16,
    val CnterWidth: Int = 2,
    val LRUWidth:   Int = 4,
) {
  val PtrWidth = log2Ceil(Count)
}

case class RetAddrStackParameters(
    val Depth: Int = 8,
    val PCLen: Int = 16,
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

object XRegAddr {
  def apply()(implicit p: HPipeParameters) = UInt(p.XRegAddrWidth.W)
}

object CsrAddr {
  def apply() = UInt(12.W) // 12 is specified by
}
