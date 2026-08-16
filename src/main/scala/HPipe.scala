package hpipe

import chisel3._
import chisel3.util._
import hammer._

class HPipeIO(implicit val p: HPipeParameters) extends Bundle {
  val instFetch = new InstFetchIO
  val memLoad   = new MemLoadIO
  val memStore  = new MemStoreIO

  val interrupt = Input(new InterruptSource)

  val retire = Output(new RetireInfo)
  val debug  = if (p.Debug) Some(Output(new DebugInfo)) else None
}

class HPipe(implicit val p: HPipeParameters) extends Module {
  val io = IO(new HPipeIO)

  val pipeIf  = Module(new PipeIf)
  val pipeId  = Module(new PipeId)
  val pipeSg  = Module(new PipeSg)
  val pipeEx  = Module(new PipeEx)
  val pipeMem = Module(new PipeMem)
  val pipeWb  = Module(new PipeWb)

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
  pipeIf.io.csr := csrFile.io.csr
  pipeSg.io.csrRead <> csrFile.io.reads(0)
  pipeEx.io.csrTransform <> csrFile.io.transforms(0)
  pipeWb.io.csrWrite <> csrFile.io.writes(0)
  pipeWb.io.csr := csrFile.io.csr

  csrFile.io.interrupt := io.interrupt
  csrFile.io.retire    := pipeWb.io.retire

  // Feed Forward
  pipeIf.io.stall :=
    pipeWb.io.busy || pipeMem.io.busy || pipeEx.io.busy || pipeSg.io.busy ||
      pipeId.io.busy

  pipeIf.io.feedForwardId  := pipeId.io.feedForward
  pipeIf.io.feedForwardSg  := pipeSg.io.feedForward
  pipeIf.io.feedForwardEx  := pipeEx.io.feedForward
  pipeIf.io.feedForwardMem := pipeMem.io.feedForward

  pipeSg.io.feedForwardEx  := pipeEx.io.feedForward
  pipeSg.io.feedForwardMem := pipeMem.io.feedForward

  // Branch
  val branch = pipeEx.io.branch
  pipeIf.io.fromEx := branch

  // Trap
  val trap = pipeWb.io.retire.trapValid
  pipeIf.io.trap := trap

  io.memStore.req.valid :=
    Mux(trap, false.B, pipeMem.io.memStore.req.valid)

  // Pipeline
  pipeId.io.fromIf := RegFlush(
    pipeIf.io.toId,
    !pipeWb.io.busy && !pipeMem.io.busy && !pipeEx.io.busy && !pipeSg.io.busy &&
      !pipeId.io.busy,
    pipeIf.io.busy || branch.redirect || trap,
  )
  pipeSg.io.fromId := RegFlush(
    pipeId.io.toSg,
    !pipeWb.io.busy && !pipeMem.io.busy && !pipeEx.io.busy && !pipeSg.io.busy,
    pipeId.io.busy || branch.redirect || trap,
  )
  pipeEx.io.fromSg := RegFlush(
    pipeSg.io.toEx,
    !pipeWb.io.busy && !pipeMem.io.busy && !pipeEx.io.busy,
    pipeSg.io.busy || branch.redirect || trap,
  )
  pipeMem.io.fromEx := RegFlush(
    pipeEx.io.toMem,
    !pipeWb.io.busy && !pipeMem.io.busy,
    pipeEx.io.busy || trap,
  )
  pipeWb.io.fromMem := RegFlush(
    pipeMem.io.toWb,
    !pipeWb.io.busy,
    pipeMem.io.busy || trap,
  )

  // Retire Observation
  io.retire := pipeWb.io.retire

  // Debug
  if (io.debug.isDefined) {
    val dbg = io.debug.get
    dbg.pcIf  := pipeIf.io.toId.pc
    dbg.pcId  := pipeId.io.toSg.pc
    dbg.pcSg  := pipeSg.io.toEx.pc
    dbg.pcEx  := pipeEx.io.toMem.pc
    dbg.pcMem := pipeMem.io.toWb.pc
    dbg.pcWb  := pipeWb.io.retire.pc

    dbg.regs := regFile.io.regs

    dbg.regInfo.zero := 0.U
    dbg.regInfo.ra   := regFile.io.regs(0)
    dbg.regInfo.sp   := regFile.io.regs(1)
    dbg.regInfo.gp   := regFile.io.regs(2)
    dbg.regInfo.tp   := regFile.io.regs(3)
    dbg.regInfo.t0   := regFile.io.regs(4)
    dbg.regInfo.t1   := regFile.io.regs(5)
    dbg.regInfo.t2   := regFile.io.regs(6)
    dbg.regInfo.s0   := regFile.io.regs(7)
    dbg.regInfo.s1   := regFile.io.regs(8)
    dbg.regInfo.a0   := regFile.io.regs(9)
    dbg.regInfo.a1   := regFile.io.regs(10)
    dbg.regInfo.a2   := regFile.io.regs(11)
    dbg.regInfo.a3   := regFile.io.regs(12)
    dbg.regInfo.a4   := regFile.io.regs(13)
    dbg.regInfo.a5   := regFile.io.regs(14)
    dbg.regInfo.a6   := regFile.io.regs(15)
    dbg.regInfo.a7   := regFile.io.regs(16)
    dbg.regInfo.s2   := regFile.io.regs(17)
    dbg.regInfo.s3   := regFile.io.regs(18)
    dbg.regInfo.s4   := regFile.io.regs(19)
    dbg.regInfo.s5   := regFile.io.regs(20)
    dbg.regInfo.s6   := regFile.io.regs(21)
    dbg.regInfo.s7   := regFile.io.regs(22)
    dbg.regInfo.s8   := regFile.io.regs(23)
    dbg.regInfo.s9   := regFile.io.regs(24)
    dbg.regInfo.s10  := regFile.io.regs(25)
    dbg.regInfo.s11  := regFile.io.regs(26)
    dbg.regInfo.t3   := regFile.io.regs(27)
    dbg.regInfo.t4   := regFile.io.regs(28)
    dbg.regInfo.t5   := regFile.io.regs(29)
    dbg.regInfo.t6   := regFile.io.regs(30)

    dbg.branch     := branch.valid
    dbg.branchMiss := branch.redirect

    dbg.csr := csrFile.io.csr
  }
}

object HPipe extends App {
  Export(new HPipe()(HPipeParameters()), args)
}

object HPipeDebug extends App {
  Export(new HPipe()(HPipeParameters(Debug = true)), args)
}

object HPipeFpga extends App {
  Export(
    new HPipe()(HPipeParameters(UseArithMacro = true)),
    args,
    Array("-lowering-options=mitigateVivadoArrayIndexConstPropBug"),
  )
}

object HPipeFpgaMini extends App {
  Export(
    new HPipe()(HPipeParameters(UseArithMacro = true, Branch = false)),
    args,
    Array("-lowering-options=mitigateVivadoArrayIndexConstPropBug"),
  )
}

object HPipeAsic extends App {
  Export(
    new HPipe()(HPipeParameters(UseArithMacro = true)),
    args,
    Array(
      "-lowering-options=disallowLocalVariables,disallowPackedArrays",
    ),
  )
}
