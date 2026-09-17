package hpipe.sim

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import hammer._
import hpipe._

class SimDebugger(hpipe: HPipe)(implicit p: HPipeParameters) extends Module {
  def get[A <: Data](source: A) = BoringUtils.tapAndRead(source)

  val retire = get(hpipe.pipeWb.io.retire)
  dontTouch(retire)

  val pipeline = WireZero(new PipelineInfo)
  dontTouch(pipeline)
  pipeline.s0If  := get(hpipe.pipeIf.io.toId)
  pipeline.s1Id  := get(hpipe.pipeId.io.fromIf)
  pipeline.s2Sg  := get(hpipe.pipeSg.io.fromId)
  pipeline.s3Ex  := get(hpipe.pipeEx.io.fromSg)
  pipeline.s4Mem := get(hpipe.pipeMem.io.fromEx)
  pipeline.s5Wb  := get(hpipe.pipeWb.io.fromMem)

  val regs = WireZero(new RegInfo)
  dontTouch(regs)
  regs.zero := 0.U
  regs.ra   := get(hpipe.regFile.io.regs(0))
  regs.sp   := get(hpipe.regFile.io.regs(1))
  regs.gp   := get(hpipe.regFile.io.regs(2))
  regs.tp   := get(hpipe.regFile.io.regs(3))
  regs.t0   := get(hpipe.regFile.io.regs(4))
  regs.t1   := get(hpipe.regFile.io.regs(5))
  regs.t2   := get(hpipe.regFile.io.regs(6))
  regs.s0   := get(hpipe.regFile.io.regs(7))
  regs.s1   := get(hpipe.regFile.io.regs(8))
  regs.a0   := get(hpipe.regFile.io.regs(9))
  regs.a1   := get(hpipe.regFile.io.regs(10))
  regs.a2   := get(hpipe.regFile.io.regs(11))
  regs.a3   := get(hpipe.regFile.io.regs(12))
  regs.a4   := get(hpipe.regFile.io.regs(13))
  regs.a5   := get(hpipe.regFile.io.regs(14))
  regs.a6   := get(hpipe.regFile.io.regs(15))
  regs.a7   := get(hpipe.regFile.io.regs(16))
  regs.s2   := get(hpipe.regFile.io.regs(17))
  regs.s3   := get(hpipe.regFile.io.regs(18))
  regs.s4   := get(hpipe.regFile.io.regs(19))
  regs.s5   := get(hpipe.regFile.io.regs(20))
  regs.s6   := get(hpipe.regFile.io.regs(21))
  regs.s7   := get(hpipe.regFile.io.regs(22))
  regs.s8   := get(hpipe.regFile.io.regs(23))
  regs.s9   := get(hpipe.regFile.io.regs(24))
  regs.s10  := get(hpipe.regFile.io.regs(25))
  regs.s11  := get(hpipe.regFile.io.regs(26))
  regs.t3   := get(hpipe.regFile.io.regs(27))
  regs.t4   := get(hpipe.regFile.io.regs(28))
  regs.t5   := get(hpipe.regFile.io.regs(29))
  regs.t6   := get(hpipe.regFile.io.regs(30))

  val csrs = get(hpipe.csrFile.io.csrs)
  dontTouch(csrs)

  val branch = get(hpipe.branch)
  dontTouch(branch)
}
