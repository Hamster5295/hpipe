package hpipe

import chisel3._
import chisel3.util._

class HPipeSocIO(implicit val p: HPipeParameters) extends Bundle {}

class HPipeSoc(implicit val p: HPipeParameters) extends Module {
  val io = IO(new HPipeSocIO)

  val cpu = Module(new HPipe)
}
