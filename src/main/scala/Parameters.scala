package hpipe

import chisel3._
import hammer._

class Parameters(
    val debug: Boolean = false,

    val XLEN: Int = 32,

    val InstWidth: Int = 32,
    val DataWidth: Int = 32,

    val ResetVector: String = "x80000000",

    // Branch
    val HistBuf:      HistBufferParameters = new HistBufferParameters,
    val RetAddrStack: RetAddrStackParameters = new RetAddrStackParameters,
) {
  val XRegAddrWidth = CLog2(XLEN)
  val AddrWidth     = DataWidth
}

class HistBufferParameters(
    val PCLen:      Int = 16,
    val Count:      Int = 16,
    val CnterWidth: Int = 2,
    val LRUWidth:   Int = 4,
) {
  val PtrWidth = CLog2(Count)
}

class RetAddrStackParameters(
    val Depth: Int = 8,
    val PCLen: Int = 16,
) {
  val PtrWidth = CLog2(Depth)
}

object Addr {
  def apply()(implicit p: Parameters) = UInt(p.AddrWidth.W)
}

object Inst {
  def apply()(implicit p: Parameters) = UInt(p.InstWidth.W)
}

object Word {
  def apply()(implicit p: Parameters) = UInt(p.DataWidth.W)
}

object XRegAddr {
  def apply()(implicit p: Parameters) = UInt(p.XRegAddrWidth.W)
}
