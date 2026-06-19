package hpipe

import chisel3._
import chisel3.util._
import hammer._
import hpipe.ALUOp._
import hpipe.BranchOp._

class PipeExIO(implicit p: HPipeParameters) extends StageIO {
  val fromId = Input(new Id2ExIO)
  val toMem  = Output(new Ex2MemIO)

  val feedForward = Output(new DestInfo)
  val branch      = Output(new BranchInfo)
}

class PipeEx(implicit val p: HPipeParameters) extends Module {
  val io     = IO(new PipeExIO)
  val fromId = io.fromId

  // ALU Op when inst is BR
  val opForBr = MuxLookup(fromId.funct, SLT)(Seq(
    EQ.asUInt  -> ADD,
    NE.asUInt  -> ADD,
    LTU.asUInt -> SLTU,
    GEU.asUInt -> SLTU,
  ))

  val op = MuxIf(
    (fromId.flags.isMem || fromId.flags.isJal || fromId.flags.isCsr) -> ADD,
    fromId.flags.isBr                                            -> opForBr,
  )(fromId.funct.asTypeOf(ALUOp()))

  val aluInv = Mux(
    fromId.flags.isBr,
    MuxLookup(fromId.funct, fromId.flags.isAluInv)(Seq(
      EQ.asUInt -> 1.B,
      NE.asUInt -> 1.B,
    )),
    fromId.flags.isAluInv,
  )

  // ALU
  val alu = Module(new ArithUnit)
  alu.io.src1 := fromId.src1
  alu.io.src2 := fromId.src2
  alu.io.op   := op
  alu.io.inv  := aluInv

  // Mul & Div
  val mdu = Module(new MulDivUnit)
  mdu.io.src1  := fromId.src1
  mdu.io.src2  := fromId.src2
  mdu.io.op    := fromId.funct
  mdu.io.valid := fromId.flags.isMulDiv

  // Branch
  val pred   = io.fromId.predInfo
  val brTake = fromId.flags.isBr && MuxLookup(fromId.funct, false.B)(Seq(
    EQ.asUInt  -> (!alu.io.result.orR),
    NE.asUInt  -> (alu.io.result.orR),
    LT.asUInt  -> (alu.io.result === 1.U),
    GE.asUInt  -> (alu.io.result === 0.U),
    LTU.asUInt -> (alu.io.result === 1.U),
    GEU.asUInt -> (alu.io.result === 0.U),
  ))
  val brMiss   = brTake ^ pred.brTake
  val jalrMiss = !(fromId.addr === pred.target)

  io.branch.flags     := pred.flags
  io.branch.pc       := fromId.pc
  io.branch.redirect :=
    !pred.flags.isJal && // Jal always goes to the correct branch
      ((brMiss && pred.flags.isBr) || (jalrMiss && pred.flags.isJalr))
  io.branch.target := Mux(
    (pred.flags.isBr && brTake) || pred.flags.isJalr,
    fromId.addr,
    pred.defaultTarget,
  )
  io.branch.brTake   := brTake
  io.branch.callAddr := alu.io.result // CALL will always take the alu result

  val result = Mux(fromId.flags.isMulDiv, mdu.io.result, alu.io.result)

  // To Mem
  val toMem = io.toMem
  toMem.valid := fromId.valid
  toMem.pc    := fromId.pc
  toMem.rd    := fromId.rd
  toMem.funct := fromId.funct
  toMem.data  := result
  toMem.addr  := fromId.addr
  toMem.flags   := fromId.flags
  toMem.csr   := fromId.csr

  // Feed Forward
  val toId = io.feedForward
  toId.rd      := fromId.rd
  toId.isWrite := fromId.flags.writeRd
  toId.isLd    := fromId.flags.isLd
  toId.data    := result

  io.busy := Mux(fromId.flags.isMulDiv, mdu.io.busy, false.B)
}
