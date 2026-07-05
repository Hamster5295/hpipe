package hpipe

import chisel3._
import chisel3.util._
import hammer._
import hpipe.ALUOp._
import hpipe.BranchOp._

class PipeExIO(implicit p: HPipeParameters) extends StageIO {
  val fromSg = Input(new Sg2ExIO)
  val toMem  = Output(new Ex2MemIO)

  val csrTransform = Flipped(new CsrTransformPort)

  val feedForward = Output(new DestInfo)
  val branch      = Output(new BranchInfo)
}

class PipeEx(implicit val p: HPipeParameters) extends Module {
  val io     = IO(new PipeExIO)
  val fromSg = io.fromSg

  // The addr for branch & mem insts
  val addr = fromSg.addrBase +% fromSg.imm

  // ALU
  val alu = Module(new ArithUnit)
  alu.io.src1 := fromSg.src1
  alu.io.src2 := fromSg.src2
  alu.io.op   := Mux(
    fromSg.flags.isMem || fromSg.flags.jal,
    Add,
    fromSg.funct.asTypeOf(ALUOp()),
  )
  alu.io.inv := fromSg.flags.aluInv

  // Mul & Div
  val mdu = Module(new MulDivUnit)
  mdu.io.src1  := fromSg.src1
  mdu.io.src2  := fromSg.src2
  mdu.io.op    := fromSg.funct
  mdu.io.valid := fromSg.flags.muldiv

  // csr
  val csrTr   = io.csrTransform
  val csrData = MuxIf(
    fromSg.flags.mret -> ("b1_1000_1000".U ## fromSg.csrSrc(7) ## "b000".U),
    (fromSg.funct === "b010".U) -> (fromSg.src1 | fromSg.csrSrc),
  )(fromSg.src1)

  csrTr.addr := fromSg.csrAddr
  csrTr.data := csrData
  val csrResult = csrTr.result

  val result = MuxIf(
    fromSg.flags.csr    -> fromSg.csrSrc,
    fromSg.flags.muldiv -> mdu.io.result,
  )(alu.io.result)

  // Branch - dedicated comparators bypass ALU for shorter critical path
  val pred   = io.fromSg.pred
  val brEq   = fromSg.src1 === fromSg.src2
  val brLt   = fromSg.src1.asSInt < fromSg.src2.asSInt
  val brLtu  = fromSg.src1 < fromSg.src2
  val brTake = fromSg.flags.br && MuxLookup(fromSg.funct, false.B)(Seq(
    EQ.asUInt  -> brEq,
    NE.asUInt  -> !brEq,
    LT.asUInt  -> brLt,
    GE.asUInt  -> !brLt,
    LTU.asUInt -> brLtu,
    GEU.asUInt -> !brLtu,
  ))

  // Parallel: target mismatch computed independently of brTake comparison
  val actualTake     = brTake || fromSg.flags.jal
  val targetMismatch = addr =/= pred.target
  val branchMiss     = (actualTake ^ pred.take) | (actualTake & pred.take & targetMismatch)

  val realTarget = Mux(actualTake, addr, pred.stepPc)

  io.branch.valid          := fromSg.flags.br || fromSg.flags.jal
  io.branch.pc             := fromSg.pc
  io.branch.flags          := pred.flags
  io.branch.take           := brTake
  io.branch.target         := addr       // Addr is the branch target when taken
  io.branch.redirect       := branchMiss && io.branch.valid
  io.branch.redirectTarget := realTarget // realTarget is the actual branch
  // (When br condition not met, realTarget falls to pc + 4)

  // Exception
  val excp = io.toMem.trap

  val brMisaligned =
    fromSg.flags.br && io.branch.target.end(if (p.ExtC) 1 else 2).orR

  excp.valid := fromSg.trap.valid | brMisaligned
  excp.cause := Mux(brMisaligned, 0.U, fromSg.trap.cause)

  // To Mem
  val toMem = io.toMem
  toMem.valid   := fromSg.valid
  toMem.pc      := fromSg.pc
  toMem.rd      := fromSg.rdAddr
  toMem.funct   := fromSg.funct
  toMem.data    := result
  toMem.addr    := addr
  toMem.flags   := fromSg.flags
  toMem.csrAddr := fromSg.csrAddr
  toMem.csrData := csrResult

  // Feed Forward
  val toId = io.feedForward
  toId.gpr.valid     := fromSg.flags.writeRd && fromSg.rdAddr.orR
  toId.gpr.bits.addr := fromSg.rdAddr
  toId.gpr.bits.data := result
  toId.gpr.bits.isLd := fromSg.flags.ld

  toId.csr.valid     := fromSg.flags.csr && fromSg.csrAddr.orR
  toId.csr.bits.addr := fromSg.csrAddr
  toId.csr.bits.data := csrResult

  io.busy := Mux(fromSg.flags.muldiv, mdu.io.busy, false.B)
}
