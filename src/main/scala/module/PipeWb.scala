package hpipe

import chisel3._
import chisel3.util._

class PipeWbIO(implicit p: Parameters) extends Bundle {
  val fromMem = Flipped(new Mem2WbIO)
  val regWrite    = Flipped(new RegFileWritePort)

  val retire = Output(new RetireInfo)
}

class PipeWb(implicit p: Parameters) extends Module {
  val io      = IO(new PipeWbIO)
  val fromMem = io.fromMem

  io.regWrite.addr := Mux(fromMem.writeRd, fromMem.rd, 0.U)
  io.regWrite.data := fromMem.data

  val retire = io.retire
  retire.valid  := fromMem.valid
  retire.pc     := fromMem.pc
  retire.ebreak := fromMem.ebreak
}
