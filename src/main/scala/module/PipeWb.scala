package hpipe

import chisel3._
import chisel3.util._

class PipeWbIO(implicit p: Parameters) extends Bundle {
  val fromMem = Flipped(new Mem2WbIO)
  val toId    = Flipped(new RegFileWritePort)

  val retire = Output(new RetireInfo)
}

class PipeWb(implicit p: Parameters) extends Module {
  val io      = IO(new PipeWbIO)
  val fromMem = io.fromMem

  val toId = io.toId
  toId.addr := Mux(fromMem.writeRd, fromMem.rd, 0.U)
  toId.data := fromMem.data

  val retire = io.retire
  retire.valid  := fromMem.valid
  retire.pc     := fromMem.pc
  retire.ebreak := fromMem.ebreak
}
