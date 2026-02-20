package hpipe

import chisel3._
import chisel3.util._
import hammer._
import hpipe.ALUOp._
import hpipe.BranchOp._

class PipeExIO(implicit p: Parameters) extends Bundle {
  val fromId = Flipped(new Id2ExIO)
  val toMem  = new Ex2MemIO

  val toId = Output(new FeedForward)
  val toIf = Output(ValidIO(Addr()))
}

class PipeEx(implicit p: Parameters) extends Module {
  val io     = IO(new PipeExIO)
  val fromId = io.fromId

  // ALU Op when inst is BR
  val opForBr = MuxLookup(fromId.funct, SLT)(Seq(
    EQ.asUInt  -> ADD,
    NE.asUInt  -> ADD,
    LTU.asUInt -> SLTU,
    GEU.asUInt -> SLTU
  ))

  val op = MuxIf(
    (fromId.uop.isMem || fromId.uop.isJal) -> ADD,
    fromId.uop.isBr                        -> opForBr
  )(fromId.funct.asTypeOf(ALUOp()))

  val aluInv = Mux(
    fromId.uop.isBr,
    MuxLookup(fromId.funct, fromId.uop.isAluInv)(Seq(
      EQ.asUInt -> 1.B,
      NE.asUInt -> 1.B
    )),
    fromId.uop.isAluInv
  )

  val alu = Module(new ALU)
  alu.io.src1 := fromId.src1
  alu.io.src2 := fromId.src2
  alu.io.op   := op
  alu.io.inv  := aluInv

  // Branch
  val brTake = fromId.uop.isBr && MuxLookup(fromId.funct, false.B)(Seq(
    EQ.asUInt  -> (!alu.io.result.orR),
    NE.asUInt  -> (alu.io.result.orR),
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
  toMem.valid := fromId.valid
  toMem.pc    := fromId.pc
  toMem.rd    := fromId.rd
  toMem.funct := fromId.funct
  toMem.alu   := alu.io.result
  toMem.addr  := fromId.addr
  toMem.uop   := fromId.uop

  // Feed Forward
  val toId = io.toId
  toId.rd      := fromId.rd
  toId.isWrite := fromId.uop.writeRd
  toId.isLd    := fromId.uop.isLd
  toId.data    := alu.io.result
}
