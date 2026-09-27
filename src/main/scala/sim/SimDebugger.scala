package hpipe.sim

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import hammer._
import hpipe._

class SimDebugger(hpipe: HPipe)(implicit p: HPipeParameters) extends Module {
  def probe[A <: Data](source: A) = BoringUtils.tapAndRead(source)

  val retire = probe(hpipe.pipeWb.io.retire)
  dontTouch(retire)

  val pipeline = WireZero(new PipelineInfo)
  dontTouch(pipeline)
  pipeline.s0If  := probe(hpipe.pipeIf.io.toId)
  pipeline.s1Id  := probe(hpipe.pipeId.io.fromIf)
  pipeline.s2Sg  := probe(hpipe.pipeSg.io.fromId)
  pipeline.s3Ex  := probe(hpipe.pipeEx.io.fromSg)
  pipeline.s4Mem := probe(hpipe.pipeMem.io.fromEx)
  pipeline.s5Wb  := probe(hpipe.pipeWb.io.fromMem)

  val regs    = WireZero(new RegInfo)
  val regFile = probe(hpipe.regFile.io)

  dontTouch(regs)
  regs.zero := 0.U
  regs.ra   := regFile.regs(0)
  regs.sp   := regFile.regs(1)
  regs.gp   := regFile.regs(2)
  regs.tp   := regFile.regs(3)
  regs.t0   := regFile.regs(4)
  regs.t1   := regFile.regs(5)
  regs.t2   := regFile.regs(6)
  regs.s0   := regFile.regs(7)
  regs.s1   := regFile.regs(8)
  regs.a0   := regFile.regs(9)
  regs.a1   := regFile.regs(10)
  regs.a2   := regFile.regs(11)
  regs.a3   := regFile.regs(12)
  regs.a4   := regFile.regs(13)
  regs.a5   := regFile.regs(14)
  regs.a6   := regFile.regs(15)
  regs.a7   := regFile.regs(16)
  regs.s2   := regFile.regs(17)
  regs.s3   := regFile.regs(18)
  regs.s4   := regFile.regs(19)
  regs.s5   := regFile.regs(20)
  regs.s6   := regFile.regs(21)
  regs.s7   := regFile.regs(22)
  regs.s8   := regFile.regs(23)
  regs.s9   := regFile.regs(24)
  regs.s10  := regFile.regs(25)
  regs.s11  := regFile.regs(26)
  regs.t3   := regFile.regs(27)
  regs.t4   := regFile.regs(28)
  regs.t5   := regFile.regs(29)
  regs.t6   := regFile.regs(30)

  val csrs = probe(hpipe.csrFile.io.csrs)
  dontTouch(csrs)

  val branch = probe(hpipe.branch)
  dontTouch(branch)
}
