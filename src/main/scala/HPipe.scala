package hpipe

import chisel3._
import chisel3.util._
import hammer._
import hpipe.sim._

class HPipeIO(implicit val p: HPipeParameters) extends Bundle {
  val instFetch = new InstFetchPort
  val memLoad   = new MemLoadPort
  val memStore  = new MemStorePort

  val interrupt = Input(new InterruptSource)

  val sim = if (p.Sim) Some(Output(new SimInterface)) else None
}

class HPipe(implicit val p: HPipeParameters) extends Module {
  val io = IO(new HPipeIO)

  val pipeIf  = Module(new PipeIf)
  val pipeId  = Module(new PipeId)
  val pipeSg  = Module(new PipeSg)
  val pipeEx  = Module(new PipeEx)
  val pipeMem = Module(new PipeMem)
  val pipeWb  = Module(new PipeWb)

  val pipes = Seq(pipeIf, pipeId, pipeSg, pipeEx, pipeMem, pipeWb).reverse
  val pipeBusyMask = pipes.map(_.io.busy).asUInt

  val regFile = Module(new RegFile)
  val csrFile = Module(new CsrFile)

  // Ports
  io.instFetch <> pipeIf.io.fetch
  pipeMem.io.memLoad <> io.memLoad
  pipeMem.io.memStore <> io.memStore

  // RegFile
  pipeSg.io.rs1Read <> regFile.io.reads(0)
  pipeSg.io.rs2Read <> regFile.io.reads(1)
  regFile.io.writes := pipeWb.io.regWrite

  // CSR
  pipeIf.io.csr := csrFile.io.csrs
  pipeSg.io.csrRead <> csrFile.io.reads(0)
  pipeEx.io.csrTransform <> csrFile.io.transforms(0)
  pipeWb.io.csrWrite <> csrFile.io.writes(0)
  pipeWb.io.csr := csrFile.io.csrs

  csrFile.io.interrupt := io.interrupt
  csrFile.io.retire    := pipeWb.io.retire

  // Feed Forward
  pipeIf.io.feedForwardId  := pipeId.io.feedForward
  pipeIf.io.feedForwardSg  := pipeSg.io.feedForward
  pipeIf.io.feedForwardEx  := pipeEx.io.feedForward
  pipeIf.io.feedForwardMem := pipeMem.io.feedForward

  pipeSg.io.feedForwardEx  := pipeEx.io.feedForward
  pipeSg.io.feedForwardMem := pipeMem.io.feedForward

  // Branch
  val branch = pipeEx.io.branch
  pipeIf.io.fromEx := branch

  // Stall
  pipeIf.io.stall := pipeBusyMask.orR

  // Trap
  val trap = pipeWb.io.retire.trapValid
  pipeIf.io.trap   := trap
  pipeMem.io.flush := trap

  io.memStore.req.valid :=
    Mux(trap, false.B, pipeMem.io.memStore.req.valid)

  pipeId.io.fromIf := RegFlush(
    pipeIf.io.toId,
    !pipeBusyMask.end(5).orR,
    (pipeIf.io.busy && !pipeBusyMask.end(4).orR) || branch.redirect || trap,
  )
  pipeSg.io.fromId := RegFlush(
    pipeId.io.toSg,
    !pipeBusyMask.end(4).orR,
    (pipeId.io.busy && !pipeBusyMask.end(3).orR) || branch.redirect || trap,
  )
  pipeEx.io.fromSg := RegFlush(
    pipeSg.io.toEx,
    !pipeBusyMask.end(3).orR,
    (pipeSg.io.busy && !pipeBusyMask.end(2).orR) || branch.redirect || trap,
  )
  pipeMem.io.fromEx := RegFlush(
    pipeEx.io.toMem,
    !pipeBusyMask.end(2).orR,
    (pipeEx.io.busy && !pipeBusyMask(0)) || trap,
  )
  pipeWb.io.fromMem := RegFlush(
    pipeMem.io.toWb,
    !pipeBusyMask(0),
    pipeMem.io.busy || trap,
  )

  // Sim
  if (p.Sim) {
    val sim     = Module(new SimDebugger(this))
    val chiperf = Module(new ChiperfLogger(this, sim))

    val si = io.sim.get
    si.retire     := pipeWb.io.retire
    si.regs       := regFile.io.regs
    si.csrs       := csrFile.io.csrs
    si.branch     := branch.valid
    si.branchMiss := branch.redirect
  }
}
