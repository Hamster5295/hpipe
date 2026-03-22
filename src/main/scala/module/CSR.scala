package hpipe

import chisel3._
import chisel3.util._

class CSRIO(implicit val p: Parameters) extends Bundle {}

class CSR(implicit val p: Parameters) extends Module {
  val io = IO(new CSRIO)
}
