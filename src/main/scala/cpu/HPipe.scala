package hpipe

import chisel3._
import chisel3.util._
import hammer._

class HPipeIO(implicit val p: HPipeParameters) extends Bundle {
  val instFetch = new InstFetchIO
  val memLoad   = new MemLoadIO
  val memStore  = new MemStoreIO

  val retire = Output(new RetireInfo)

  val debug = if (p.Debug) Some(Output(new DebugInfo)) else None
}

class HPipe(implicit val p: HPipeParameters) extends Module {
  val io = IO(new HPipeIO)

  val pipeIf  = Module(new PipeIf)
  val pipeId  = Module(new PipeId)
  val pipeEx  = Module(new PipeEx)
  val pipeMem = Module(new PipeMem)
  val pipeWb  = Module(new PipeWb)

  val regFile = Module(new RegFile)
  val csr     = Module(new CsrFile)

  // Ports
  io.instFetch <> pipeIf.io.fetch
  pipeMem.io.memLoad <> io.memLoad
  pipeMem.io.memStore <> io.memStore

  // RegFile
  pipeId.io.rs1Read <> regFile.io.read(0)
  pipeId.io.rs2Read <> regFile.io.read(1)
  pipeIf.io.regRead <> regFile.io.read(2)
  regFile.io.write := pipeWb.io.regWrite

  // CSR
  pipeWb.io.csrRead <> csr.io.read(0)
  pipeWb.io.csrWrite <> csr.io.write(0)

  // Feed Forward
  pipeIf.io.stall :=
    pipeWb.io.busy || pipeMem.io.busy || pipeEx.io.busy || pipeId.io.busy

  pipeIf.io.feedForwardId  := pipeId.io.feedForward
  pipeIf.io.feedForwardEx  := pipeEx.io.feedForward
  pipeIf.io.feedForwardMem := pipeMem.io.feedForward

  pipeId.io.fromEx  := pipeEx.io.feedForward
  pipeId.io.fromMem := pipeMem.io.feedForward

  // Branch
  val branch = pipeEx.io.branch
  pipeIf.io.fromEx := branch

  // Pipeline
  pipeId.io.fromIf := RegFlush(
    pipeIf.io.toId,
    !pipeWb.io.busy && !pipeMem.io.busy && !pipeEx.io.busy && !pipeId.io.busy,
    pipeIf.io.busy || branch.redirect,
  )
  pipeEx.io.fromId := RegFlush(
    pipeId.io.toEx,
    !pipeWb.io.busy && !pipeMem.io.busy && !pipeEx.io.busy,
    pipeId.io.busy || branch.redirect,
  )
  pipeMem.io.fromEx := RegFlush(
    pipeEx.io.toMem,
    !pipeWb.io.busy && !pipeMem.io.busy,
    pipeEx.io.busy,
  )
  pipeWb.io.fromMem := RegFlush(
    pipeMem.io.toWb,
    !pipeWb.io.busy,
    pipeMem.io.busy,
  )

  // Retire Observation
  io.retire := pipeWb.io.retire

  // Debug
  if (io.debug.isDefined) {
    val dbg = io.debug.get
    dbg.pcIf  := pipeIf.io.toId.pc
    dbg.pcId  := pipeId.io.toEx.pc
    dbg.pcEx  := pipeEx.io.toMem.pc
    dbg.pcMem := pipeMem.io.toWb.pc
    dbg.pcWb  := pipeWb.io.retire.pc

    dbg.regs := regFile.io.regs
  }
}

object HPipe extends App {
  Export(new HPipe()(HPipeParameters()), args)
}

object HPipeDebug extends App {
  Export(new HPipe()(HPipeParameters(Debug = true)), args)
}
