package hpipe

import chisel3._
import hammer._

class Parameters(
    val debug: Boolean = false,

    val XLEN: Int = 32,

    val InstWidth: Int = 32,
    val DataWidth: Int = 32,

    val ResetVector: String = "x80000000"
) {
  val XRegAddrWidth = CLog2(XLEN)

  val AddrWidth = DataWidth
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
