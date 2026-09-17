package hpipe.sim

import chisel3._
import chisel3.util._
import hammer._
import hpipe._

class PipelineInfo(implicit p: HPipeParameters) extends Bundle {
  val s0If  = new PipeIO
  val s1Id  = new PipeIO
  val s2Sg  = new PipeIO
  val s3Ex  = new PipeIO
  val s4Mem = new PipeIO
  val s5Wb  = new PipeIO
}

class RegInfo(implicit p: HPipeParameters) extends Bundle {
  val zero = Word()
  val ra   = Word()
  val sp   = Word()
  val gp   = Word()
  val tp   = Word()
  val t0   = Word()
  val t1   = Word()
  val t2   = Word()
  val s0   = Word()
  val s1   = Word()
  val a0   = Word()
  val a1   = Word()
  val a2   = Word()
  val a3   = Word()
  val a4   = Word()
  val a5   = Word()
  val a6   = Word()
  val a7   = Word()
  val s2   = Word()
  val s3   = Word()
  val s4   = Word()
  val s5   = Word()
  val s6   = Word()
  val s7   = Word()
  val s8   = Word()
  val s9   = Word()
  val s10  = Word()
  val s11  = Word()
  val t3   = Word()
  val t4   = Word()
  val t5   = Word()
  val t6   = Word()
}

class SimInterface(implicit p: HPipeParameters) extends Bundle {
  val retire = new RetireInfo
  val regs   = Vec(p.XLEN - 1, Word())
  val csrs   = new Csr

  val branch     = new Bool
  val branchMiss = new Bool
}
