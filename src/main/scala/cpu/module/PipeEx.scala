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
    EQ.asUInt  -> XOR,
    NE.asUInt  -> XOR,
    LT.asUInt  -> SLT,
    GE.asUInt  -> SLT,
    LTU.asUInt -> SLTU,
    GEU.asUInt -> SLTU,
  ))

  val op = MuxIf(
    (fromId.flags.isMem || fromId.flags.jal || fromId.flags.csr) -> ADD,
    fromId.flags.br                                              -> opForBr,
  )(fromId.funct.asTypeOf(ALUOp()))

  val aluInv = Mux(
    fromId.flags.br,
    MuxLookup(fromId.funct, fromId.flags.aluInv)(Seq(
      LT.asUInt  -> 1.B,
      GE.asUInt  -> 1.B,
      LTU.asUInt -> 1.B,
      GEU.asUInt -> 1.B,
    )),
    fromId.flags.aluInv,
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
  mdu.io.valid := fromId.flags.muldiv

  // csr
  val csrResult = MuxIf(
    fromId.flags.mret -> ("b1_1000_1000".U ## fromId.csrSrc(7) ## "b000".U),
    (fromId.funct === "b010".U) -> (fromId.src1 | fromId.csrSrc),
  )(fromId.src1)

  val result = MuxIf(
    fromId.flags.csr    -> fromId.csrSrc,
    fromId.flags.muldiv -> mdu.io.result,
  )(alu.io.result)

  // Branch
  val aluResult = alu.io.result

  val pred   = io.fromId.predInfo
  val brTake = fromId.flags.br && MuxLookup(fromId.funct, false.B)(Seq(
    EQ.asUInt  -> !aluResult.orR,
    NE.asUInt  -> aluResult.orR,
    LT.asUInt  -> aluResult(0),
    GE.asUInt  -> !aluResult(0),
    LTU.asUInt -> aluResult(0),
    GEU.asUInt -> !aluResult(0),
  ))
  val brMiss   = brTake ^ pred.brTake
  val jalrMiss = !(fromId.addr === pred.target)

  io.branch.flags    := pred.flags
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

  // To Mem
  val toMem = io.toMem
  toMem.valid   := fromId.valid
  toMem.pc      := fromId.pc
  toMem.rd      := fromId.rd
  toMem.funct   := fromId.funct
  toMem.data    := result
  toMem.addr    := fromId.addr
  toMem.flags   := fromId.flags
  toMem.csrAddr := fromId.csrAddr
  toMem.csrData := csrResult

  // Feed Forward
  val toId = io.feedForward
  toId.gpr.valid     := fromId.flags.writeRd && fromId.rd.orR
  toId.gpr.bits.addr := fromId.rd
  toId.gpr.bits.data := result
  toId.gpr.bits.isLd := fromId.flags.ld

  toId.csr.valid     := fromId.flags.csr && fromId.csrAddr.orR
  toId.csr.bits.addr := fromId.csrAddr
  toId.csr.bits.data := csrResult

  io.busy := Mux(fromId.flags.muldiv, mdu.io.busy, false.B)
}
