package hpipe

import chisel3._
import chisel3.util._
import hammer._
import hpipe.ALUOp._
import hpipe.BranchOp._

class PipeExIO(implicit val p: Parameters) extends Bundle {
  val fromId = Flipped(new Id2ExIO)
  val toMem  = new Ex2MemIO

  val toId = Output(new FeedForward)
  val toIf = Output(ValidIO(Addr()))
}

class PipeEx(implicit val p: Parameters) extends Module {
  val io     = IO(new PipeExIO)
  val fromId = io.fromId

  // ALU Op when inst is BR
  val opForBr = MuxLookup(fromId.funct, SLT)(Seq(
    LTU.asUInt -> SLTU,
    GEU.asUInt -> SLTU
  ))

  val op = MuxIf(
    fromId.uop.isMem -> ADD,
    fromId.uop.isBr  -> opForBr
  )(fromId.funct.asTypeOf(ALUOp()))

  val alu = Module(new ALU)
  alu.io.src1 := fromId.src1
  alu.io.src2 := fromId.src2
  alu.io.op   := op
  alu.io.sra  := fromId.uop.isSra

  // Branch
  val brTake = MuxLookup(fromId.funct, false.B)(Seq(
    EQ.asUInt  -> (alu.io.result === 0.U),
    NE.asUInt  -> (alu.io.result === 1.U),
    LT.asUInt  -> (alu.io.result === 1.U),
    GE.asUInt  -> (alu.io.result === 0.U),
    LTU.asUInt -> (alu.io.result === 1.U),
    GEU.asUInt -> (alu.io.result === 0.U)
  ))
  val jalTake = fromId.uop.isJal
  val take    = brTake || jalTake
  io.toIf.valid := take
  io.toIf.bits  := fromId.addr

  // To Mem
  val toMem = io.toMem
  toMem.pc    := fromId.pc
  toMem.rd    := fromId.rd
  toMem.funct := fromId.funct
  toMem.alu   := alu.io.result
  toMem.addr  := fromId.addr
  toMem.uop   := fromId.uop

  // Feed Forward
  val toId = io.toId
  toId.writeRd := fromId.uop.writeRd
  toId.rd      := fromId.rd
  toId.data    := alu.io.result
}
