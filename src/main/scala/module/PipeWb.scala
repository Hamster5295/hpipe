package hpipe

import chisel3._
import chisel3.util._
import hammer._

class PipeWbIO(implicit p: HPipeParameters) extends StageIO {
  val fromMem  = Input(new Mem2WbIO)
  val regWrite = Flipped(new RegFileWritePort)

  val csrWrite = Flipped(new CsrWritePort)

  val retire = Output(new RetireInfo)
}

class PipeWb(implicit val p: HPipeParameters) extends Module {
  val io      = IO(new PipeWbIO)
  val fromMem = io.fromMem

  io.busy := false.B

  io.regWrite.addr := Mux(fromMem.flags.writeRd, fromMem.rd, 0.U)
  io.regWrite.data := fromMem.data

  io.csrWrite.addr := Mux(fromMem.flags.csr, fromMem.csrAddr, 0.U)
  io.csrWrite.data := fromMem.csrData

  val retire = io.retire
  retire.valid     := fromMem.valid
  retire.pc        := fromMem.pc
  retire.exception := fromMem.exception
  retire.ebreak    := fromMem.flags.ebreak
}
