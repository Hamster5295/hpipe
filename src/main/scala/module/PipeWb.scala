package hpipe

import chisel3._
import chisel3.util._
import hammer._

class PipeWbIO(implicit p: Parameters) extends StageIO {
  val fromMem  = Input(new Mem2WbIO)
  val regWrite = Flipped(new RegFileWritePort)

  val csrRead  = Flipped(new CsrReadPort)
  val csrWrite = Flipped(new CsrWritePort)

  val retire = Output(new RetireInfo)
}

class PipeWb(implicit val p: Parameters) extends Module {
  val io      = IO(new PipeWbIO)
  val fromMem = io.fromMem

  io.busy := false.B

  io.csrRead.addr := fromMem.csr
  val dest = Mux(fromMem.isCsr, io.csrRead.data, fromMem.data)

  io.regWrite.addr := Mux(fromMem.writeRd, fromMem.rd, 0.U)
  io.regWrite.data := dest

  val csrDest = Mux(
    fromMem.csrOp === "b010".U,
    fromMem.data | io.csrRead.data,
    fromMem.data,
  )
  io.csrWrite.addr := fromMem.csr
  io.csrWrite.data := csrDest

  val retire = io.retire
  retire.valid  := fromMem.valid
  retire.pc     := fromMem.pc
  retire.ebreak := fromMem.isEBreak
}
