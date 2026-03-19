package hpipe

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._
import hammer._
import hpipe.Insts._
import hpipe.InstType._

class PipeIdIO(implicit p: Parameters) extends StageIO {
  val fromIf = Flipped(new If2IdIO)
  val toEx   = new Id2ExIO

  val fromEx  = Input(new FeedForward)
  val fromMem = Input(new FeedForward)

  val rs1Read = Flipped(new RegFileReadPort)
  val rs2Read = Flipped(new RegFileReadPort)

  val feedForward = Output(new FeedForward)
}

class PipeId(implicit p: Parameters) extends Module {
  val io = IO(new PipeIdIO)

  val toEx = io.toEx
  val inst = io.fromIf.inst
  toEx.pc     := io.fromIf.pc
  toEx.brTake := io.fromIf.brTake

  val rs1Addr = inst(19, 15)
  val rs2Addr = inst(24, 20)
  val rdAddr  = inst(11, 7)

  // UOp
  def parse(
      instType: InstType.Type,
      rs1:      Src1.Type,
      rs2:      Src2.Type,
      addrType: Boolean,

      rd:     Boolean,
      br:     Boolean,
      ld:     Boolean,
      st:     Boolean,
      jal:    Boolean,
      aluInv: Boolean,
      ebreak: Boolean,

      mext: Boolean,
  ) =
    BitPat(
      s"b1" // Inst is valid
        ++ s"${instType.asUInt.toBin(3)}"
        ++ rs1.asUInt.toBin(2)
        ++ rs2.asUInt.toBin(2)
        ++ s"${if (addrType) 1 else 0}"
        ++ s"${if (rd) 1 else 0}"
        ++ s"${if (br) 1 else 0}"
        ++ s"${if (ld) 1 else 0}"
        ++ s"${if (st) 1 else 0}"
        ++ s"${if (jal) 1 else 0}"
        ++ s"${if (aluInv) 1 else 0}"
        ++ s"${if (ebreak) 1 else 0}"
        ++ s"${if (mext) 1 else 0}",
    )

    def T = true
    def F = false

  val instTable = TruthTable(
    Map(
// format: off

      // I
      LUI    -> parse(U, Src1.None, Src2.Imm,  F, T, F, F, F, F, F, F, F),
      AUIPC  -> parse(U, Src1.PC,   Src2.Imm,  F, T, F, F, F, F, F, F, F),
      JAL    -> parse(J, Src1.PC,   Src2.Four, F, T, F, F, F, T, F, F, F),
      JALR   -> parse(I, Src1.PC,   Src2.Four, T, T, F, F, F, T, F, F, F),
      BEQ    -> parse(B, Src1.Reg,  Src2.Reg,  F, F, T, F, F, F, F, F, F),
      BNE    -> parse(B, Src1.Reg,  Src2.Reg,  F, F, T, F, F, F, F, F, F),
      BLT    -> parse(B, Src1.Reg,  Src2.Reg,  F, F, T, F, F, F, F, F, F),
      BGE    -> parse(B, Src1.Reg,  Src2.Reg,  F, F, T, F, F, F, F, F, F),
      BLTU   -> parse(B, Src1.Reg,  Src2.Reg,  F, F, T, F, F, F, F, F, F),
      BGEU   -> parse(B, Src1.Reg,  Src2.Reg,  F, F, T, F, F, F, F, F, F),
      LB     -> parse(I, Src1.None, Src2.Reg,  T, T, F, T, F, F, F, F, F),
      LH     -> parse(I, Src1.None, Src2.Reg,  T, T, F, T, F, F, F, F, F),
      LW     -> parse(I, Src1.None, Src2.Reg,  T, T, F, T, F, F, F, F, F),
      LBU    -> parse(I, Src1.None, Src2.Reg,  T, T, F, T, F, F, F, F, F),
      LHU    -> parse(I, Src1.None, Src2.Reg,  T, T, F, T, F, F, F, F, F),
      SB     -> parse(S, Src1.None, Src2.Reg,  T, F, F, F, T, F, F, F, F),
      SH     -> parse(S, Src1.None, Src2.Reg,  T, F, F, F, T, F, F, F, F),
      SW     -> parse(S, Src1.None, Src2.Reg,  T, F, F, F, T, F, F, F, F),
      ADDI   -> parse(I, Src1.Reg,  Src2.Imm,  F, T, F, F, F, F, F, F, F),
      SLTI   -> parse(I, Src1.Reg,  Src2.Imm,  F, T, F, F, F, F, F, F, F),
      SLTIU  -> parse(I, Src1.Reg,  Src2.Imm,  F, T, F, F, F, F, F, F, F),
      XORI   -> parse(I, Src1.Reg,  Src2.Imm,  F, T, F, F, F, F, F, F, F),
      ORI    -> parse(I, Src1.Reg,  Src2.Imm,  F, T, F, F, F, F, F, F, F),
      ANDI   -> parse(I, Src1.Reg,  Src2.Imm,  F, T, F, F, F, F, F, F, F),
      SLLI   -> parse(I, Src1.Reg,  Src2.Imm,  F, T, F, F, F, F, F, F, F),
      SRLI   -> parse(I, Src1.Reg,  Src2.Imm,  F, T, F, F, F, F, F, F, F),
      SRAI   -> parse(I, Src1.Reg,  Src2.Imm,  F, T, F, F, F, F, T, F, F),
      ADD    -> parse(R, Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, F),
      SUB    -> parse(R, Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, T, F, F),
      SLL    -> parse(R, Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, F),
      SLT    -> parse(R, Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, F),
      SLTU   -> parse(R, Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, F),
      XOR    -> parse(R, Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, F),
      SRL    -> parse(R, Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, F),
      SRA    -> parse(R, Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, T, F, F),
      OR     -> parse(R, Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, F),
      AND    -> parse(R, Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, F),
//    ECALL  -> parse(N, Src1.None, Src2.None, F, F, F, F, F, F, F, F, F),
      EBREAK -> parse(N, Src1.None, Src2.None, F, F, F, F, F, F, F, T, F),

      // M Extension
      MUL    -> parse(R, Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, T),
      MULH   -> parse(R, Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, T),
      MULHSU -> parse(R, Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, T),
      MULHU  -> parse(R, Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, T),
      DIV    -> parse(R, Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, T),
      DIVU   -> parse(R, Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, T),
      REM    -> parse(R, Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, T),
      REMU   -> parse(R, Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, T),

// format: on
    ),
    BitPat(0.U(15.W)),
  )
  val result = decoder(inst, instTable)

  val valid    = result.msb()
  val instType = result.get(-2, -4).asTypeOf(InstType())
  val src1     = Src1.safe(result.get(-5, -6))._1
  val src2     = Src2.safe(result.get(-7, -8))._1
  val addrType = result.get(-9)

  toEx.valid := io.fromIf.valid && valid
  toEx.uop   := result.tail(9).asTypeOf(new UOp)

  // Regs & Imm

  toEx.rs1   := rs1Addr
  toEx.rs2   := rs2Addr
  toEx.rd    := rdAddr
  toEx.funct := Mux(instType === U, 0.U, inst(14, 12))
  val imm =
    MuxLookup(instType, 0.U(32.W))(
      Seq(
        I -> SignExt(inst(31, 20), 32),
        S -> SignExt(inst(31, 25) ## inst(11, 7), 32),
        B -> SignExt(
          inst(31) ## inst(7) ## inst(30, 25) ## inst(11, 8) ## 0.U(1.W),
          32,
        ),
        U -> inst(31, 12) ## 0.U(12.W),
        J -> SignExt(
          inst(31) ## inst(19, 12) ## inst(20) ## inst(30, 21) ## 0.U(1.W),
          32,
        ),
      ),
    )

  // RegFile
  io.rs1Read.addr := toEx.rs1
  io.rs2Read.addr := toEx.rs2

  // Feed Forward
  val useRs1 = src1 === Src1.Reg || addrType
  val rs1    = MuxIf(
    (io.fromEx.isValid(rs1Addr) && useRs1)  -> io.fromEx.data,
    (io.fromMem.isValid(rs1Addr) && useRs1) -> io.fromMem.data,
  )(io.rs1Read.data)

  val useRs2 = (src2 === Src2.Reg) && rs2Addr.orR
  val rs2    = MuxIf(
    (io.fromEx.isValid(rs2Addr) && useRs2)  -> io.fromEx.data,
    (io.fromMem.isValid(rs2Addr) && useRs2) -> io.fromMem.data,
  )(io.rs2Read.data)

  val ldUseStall = io.fromEx.isLd &&
    ((useRs1 && io.fromEx.isValid(rs1Addr)) ||
      (useRs2 && io.fromEx.isValid(rs2Addr)))

  // Operator selection
  toEx.src1 := MuxLookup(src1, 0.U)(
    Seq(
      Src1.Reg -> rs1,
      Src1.PC  -> io.fromIf.pc,
    ),
  )
  toEx.src2 := MuxLookup(src2, 0.U)(
    Seq(
      Src2.Reg  -> rs2,
      Src2.Imm  -> imm,
      Src2.Four -> 4.U,
    ),
  )

  // Addr Gen
  toEx.addr := Mux(addrType, rs1, io.fromIf.pc) +% imm

  // Valid & Ready
  io.busy := ldUseStall

  // Feed forward to IF (BTB)
  val ff = io.feedForward
  ff.rd      := io.toEx.rd
  ff.isLd    := 0.U // Dont Care
  ff.isWrite := io.toEx.uop.writeRd
  ff.data    := 0.U // Dont Care
}
