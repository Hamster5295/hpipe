package hpipe

import chisel3._
import chisel3.util._
import hammer._

class CPUIO(implicit p: Parameters) extends Bundle {
  val instFetch = new InstFetchIO
  val memLoad   = new MemLoadIO
  val memStore  = new MemStoreIO

  val retire = Output(new RetireInfo)

  val debug = if (p.debug) Output(new DebugInfo) else null
}

class CPU(implicit p: Parameters) extends Module {
  val io = IO(new CPUIO)

  val pipeIf  = Module(new PipeIf)
  val pipeId  = Module(new PipeId)
  val pipeEx  = Module(new PipeEx)
  val pipeMem = Module(new PipeMem)
  val pipeWb  = Module(new PipeWb)

  val regFile = Module(new RegFile)

  // Ports
  io.instFetch <> pipeIf.io.fetch
  pipeMem.io.memLoad <> io.memLoad
  pipeMem.io.memStore <> io.memStore

  // RegFile
  pipeId.io.rs1Read <> regFile.io.read(0)
  pipeId.io.rs2Read <> regFile.io.read(1)
  pipeIf.io.regRead <> regFile.io.read(2)
  regFile.io.write := pipeWb.io.regWrite

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
  if (p.debug) {
    io.debug.pcIf  := pipeIf.io.toId.pc
    io.debug.pcId  := pipeId.io.toEx.pc
    io.debug.pcEx  := pipeEx.io.toMem.pc
    io.debug.pcMem := pipeMem.io.toWb.pc
    io.debug.pcWb  := pipeWb.io.retire.pc

    io.debug.regs := regFile.io.regs
  }
}

object CPU extends App {
  Export(
    new CPU()(new Parameters()),
    "build",
    withOutputBuffer = false,
    withPathPrefix = false,
  )
}

object CPUSim extends App {
  Export(
    new CPU()(new Parameters(debug = true)),
    "sim/rtl",
    withOutputBuffer = false,
    withPathPrefix = false,
  )
}

object CPUBackend extends App {
  Export(
    new CPU()(new Parameters()),
    "backend/rtl",
    Array( // Make yosys happy
      "--lowering-options=disallowLocalVariables,disallowPackedArrays",
    ),
    withOutputBuffer = false,
    withPathPrefix = false,
    splitVerilog = false,
  )
}
