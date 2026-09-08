package hpipe

import chisel3._
import chisel3.util.log2Ceil
import hammer._

case class HPipeParameters(
    val Sim:           Boolean = false,
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
