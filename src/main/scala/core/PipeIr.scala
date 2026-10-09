package hpipe

import chisel3._
import chisel3.util._
import hammer._

class PipeIrIO(implicit p: HPipeParameters) extends Bundle {
  val req = new CoreReadPort
}

class PipeIr extends Module {}
