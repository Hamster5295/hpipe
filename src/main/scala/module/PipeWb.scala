package hpipe

import chisel3._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chisel3.util._
import hammer._

class PipeWbIO(implicit p: HPipeParameters) extends StageIO {
  val fromMem  = Input(new Mem2WbIO)
  val regWrite = Flipped(new RegFileWritePort)
  val csrWrite = Flipped(new CsrWritePort)

  val csr = Input(new Csr)

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

  // Interrupt
  val csr       = io.csr
  val mie       = csr.mstatus(3)
  val intrMask  = (csr.mie & csr.mip).end(16)
  val intr      = intrMask.orR && mie
  val intrCause = InvPriorityEncoder(intrMask)

  val retire = io.retire
  retire.valid      := fromMem.valid
  retire.pc         := fromMem.pc
  retire.trap.valid := fromMem.trap.valid || intr
  retire.trap.cause := Mux(intr, "x8000_0000".U | intrCause, fromMem.trap.cause)
  retire.ebreak     := fromMem.flags.ebreak
}
