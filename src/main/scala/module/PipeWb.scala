package hpipe

import chisel3._
import chisel3.util._

class PipeWbIO(implicit val p: Parameters) extends Bundle {
  val fromMem = Flipped(new Mem2WbIO)
  val toId    = Flipped(new RegFileWritePort)
}

class PipeWb(implicit val p: Parameters) extends Module {
  val io      = IO(new PipeWbIO)
  val fromMem = io.fromMem
  val toId    = io.toId

  toId.addr := Mux(fromMem.writeRd, fromMem.rd, 0.U)
  toId.data := fromMem.data
}
