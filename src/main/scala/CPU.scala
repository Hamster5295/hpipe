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

  // Ports
  io.instFetch <> pipeIf.io.fetch
  pipeMem.io.memLoad <> io.memLoad
  pipeMem.io.memStore <> io.memStore

  // Writeback
  pipeId.io.fromWb := pipeWb.io.toId

  // Feed Forward
  pipeId.io.fromEx  := pipeEx.io.toId
  pipeId.io.fromMem := pipeMem.io.toId

  // Branch
  val branch = pipeEx.io.toIf
  pipeIf.io.fromEx := branch

  // Pipeline
  pipeId.io.fromIf  := RegNext(Mux(branch.valid, Zero(pipeIf.io.toId), pipeIf.io.toId))
  pipeEx.io.fromId  := RegNext(Mux(branch.valid, Zero(pipeId.io.toEx), pipeId.io.toEx))
  pipeMem.io.fromEx := RegNext(pipeEx.io.toMem)
  pipeWb.io.fromMem := RegNext(pipeMem.io.toWb)

  // Retire Observation
  io.retire := pipeWb.io.retire

  // Debug
  if (p.debug) {
    io.debug.pcIf  := pipeIf.io.toId.pc
    io.debug.pcId  := pipeId.io.toEx.pc
    io.debug.pcEx  := pipeEx.io.toMem.pc
    io.debug.pcMem := pipeMem.io.toWb.pc
    io.debug.pcWb  := pipeWb.io.retire.pc

    io.debug.regs := pipeId.io.regs
  }
}

object CPU extends App {
  Export(
    new CPU()(new Parameters()),
    "build",
    withOutputBuffer = false,
    withPathPrefix = false
  )
}

object CPUSim extends App {
  Export(
    new CPU()(new Parameters(debug = true)),
    "sim/rtl",
    withOutputBuffer = false,
    withPathPrefix = false
  )
}
