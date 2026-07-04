package hpipe

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._
import hammer._
import hpipe.Insts._
import hpipe.InstType._

case class InstInfo(
    inst: BitPat,
    typ:  InstType.Type = InstType.N,
    rs1:  Src1.Type = Src1.None,
    rs2:  Src2.Type = Src2.None,

    useRs1ForAddr: Boolean = false,

    rd:     Boolean = true,
    br:     Boolean = false,
    ld:     Boolean = false,
    st:     Boolean = false,
    jal:    Boolean = false,
    aluInv: Boolean = false,
    ecall:  Boolean = false,
    ebreak: Boolean = false,
    mret:   Boolean = false,

    m:   Boolean = false,
    csr: Boolean = false,
) {
  def toBitPat =
    BitPat(s"b1" // Inst is valid
      ++ s"${typ.asUInt.toBin(3)}"
      ++ rs1.asUInt.toBin(2)
      ++ rs2.asUInt.toBin(2)
      ++ s"${if (useRs1ForAddr) 1 else 0}"
      ++ s"${if (rd) 1 else 0}"
      ++ s"${if (br) 1 else 0}"
      ++ s"${if (ld) 1 else 0}"
      ++ s"${if (st) 1 else 0}"
      ++ s"${if (jal) 1 else 0}"
      ++ s"${if (aluInv) 1 else 0}"
      ++ s"${if (ecall) 1 else 0}"
      ++ s"${if (ebreak) 1 else 0}"
      ++ s"${if (mret) 1 else 0}"
      ++ s"${if (m) 1 else 0}"
      ++ s"${if (csr) 1 else 0}")
}

class DecodeResult(implicit p: HPipeParameters) extends Bundle {
  val valid = Bool()
  val src1  = Src1()
  val src2  = Src2()
  val funct = UInt(3.W)
  val imm   = UInt(32.W)
  val flags = new OpFlags

  val useRs1        = Bool()
  val useRs2        = Bool()
  val useRs1ForAddr = Bool()
}

class DecoderIO(implicit p: HPipeParameters) extends Bundle {
  val inst = Input(Word())

  val result = Output(new DecodeResult)
}

class Decoder(implicit p: HPipeParameters) extends Module {
  val io = IO(new DecoderIO)

  val inst  = io.inst
  val insts = Seq(
    // format: off

    // I
    InstInfo(LUI,    U,   Src1.None, Src2.Imm ),
    InstInfo(AUIPC,  U,   Src1.PC,   Src2.Imm ),

    InstInfo(JAL,    J,   Src1.PC,   Src2.Four, jal = true),
    InstInfo(JALR,   I,   Src1.PC,   Src2.Four, jal = true, useRs1ForAddr = true),

    InstInfo(BEQ,    B,   Src1.Reg,  Src2.Reg,  br = true, rd = false),
    InstInfo(BNE,    B,   Src1.Reg,  Src2.Reg,  br = true, rd = false),
    InstInfo(BLT,    B,   Src1.Reg,  Src2.Reg,  br = true, rd = false),
    InstInfo(BGE,    B,   Src1.Reg,  Src2.Reg,  br = true, rd = false),
    InstInfo(BLTU,   B,   Src1.Reg,  Src2.Reg,  br = true, rd = false),
    InstInfo(BGEU,   B,   Src1.Reg,  Src2.Reg,  br = true, rd = false),

    InstInfo(LB,     I,   Src1.None, Src2.None, ld = true, useRs1ForAddr = true),
    InstInfo(LH,     I,   Src1.None, Src2.None, ld = true, useRs1ForAddr = true),
    InstInfo(LW,     I,   Src1.None, Src2.None, ld = true, useRs1ForAddr = true),
    InstInfo(LBU,    I,   Src1.None, Src2.None, ld = true, useRs1ForAddr = true),
    InstInfo(LHU,    I,   Src1.None, Src2.None, ld = true, useRs1ForAddr = true),

    InstInfo(SB,     S,   Src1.None, Src2.Reg,  st = true, rd = false, useRs1ForAddr = true),
    InstInfo(SH,     S,   Src1.None, Src2.Reg,  st = true, rd = false, useRs1ForAddr = true),
    InstInfo(SW,     S,   Src1.None, Src2.Reg,  st = true, rd = false, useRs1ForAddr = true),

    InstInfo(ADDI,   I,   Src1.Reg,  Src2.Imm ),
    InstInfo(SLTI,   I,   Src1.Reg,  Src2.Imm ),
    InstInfo(SLTIU,  I,   Src1.Reg,  Src2.Imm ),
    InstInfo(XORI,   I,   Src1.Reg,  Src2.Imm ),
    InstInfo(ORI,    I,   Src1.Reg,  Src2.Imm ),
    InstInfo(ANDI,   I,   Src1.Reg,  Src2.Imm ),
    InstInfo(SLLI,   I,   Src1.Reg,  Src2.Imm ),
    InstInfo(SRLI,   I,   Src1.Reg,  Src2.Imm ),
    InstInfo(SRAI,   I,   Src1.Reg,  Src2.Imm,  aluInv = true),

    InstInfo(ADD,    R,   Src1.Reg,  Src2.Reg ),
    InstInfo(SUB,    R,   Src1.Reg,  Src2.Reg,  aluInv = true),
    InstInfo(SLL,    R,   Src1.Reg,  Src2.Reg ),
    InstInfo(SLT,    R,   Src1.Reg,  Src2.Reg ),
    InstInfo(SLTU,   R,   Src1.Reg,  Src2.Reg ),
    InstInfo(XOR,    R,   Src1.Reg,  Src2.Reg ),
    InstInfo(SRL,    R,   Src1.Reg,  Src2.Reg ),
    InstInfo(SRA,    R,   Src1.Reg,  Src2.Reg,  aluInv = true),
    InstInfo(OR,     R,   Src1.Reg,  Src2.Reg ),
    InstInfo(AND,    R,   Src1.Reg,  Src2.Reg ),

    InstInfo(ECALL,  rd = false, ecall  = true),
    InstInfo(EBREAK, rd = false, ebreak = true),
    InstInfo(MRET,   rd = false, mret   = true),

    // M
    InstInfo(MUL,    R,   Src1.Reg,  Src2.Reg,  m = true),
    InstInfo(MULH,   R,   Src1.Reg,  Src2.Reg,  m = true),
    InstInfo(MULHSU, R,   Src1.Reg,  Src2.Reg,  m = true),
    InstInfo(MULHU,  R,   Src1.Reg,  Src2.Reg,  m = true),
    InstInfo(DIV,    R,   Src1.Reg,  Src2.Reg,  m = true),
    InstInfo(DIVU,   R,   Src1.Reg,  Src2.Reg,  m = true),
    InstInfo(REM,    R,   Src1.Reg,  Src2.Reg,  m = true),
    InstInfo(REMU,   R,   Src1.Reg,  Src2.Reg,  m = true),

    // Zicsr
    InstInfo(CSRRW,  Csr, Src1.Reg,  Src2.None, csr = true),
    InstInfo(CSRRS,  Csr, Src1.Reg,  Src2.None, csr = true),
    InstInfo(CSRRC,  Csr, Src1.None, Src2.None, csr = true),
    InstInfo(CSRRWI, Csr, Src1.Imm,  Src2.None, csr = true),
    InstInfo(CSRRSI, Csr, Src1.Imm,  Src2.None, csr = true),
    InstInfo(CSRRCI, Csr, Src1.None, Src2.None, csr = true),

    // format: on
  )

  val table =
    TruthTable(insts.map(i => (i.inst, i.toBitPat)).toMap, BitPat.N(19))
  val result = decoder(inst, table)

  val valid         = result.msb()
  val instType      = InstType.safe(result.get(-2, -4))._1
  val src1          = Src1.safe(result.get(-5, -6))._1
  val src2          = Src2.safe(result.get(-7, -8))._1
  val useRs1ForAddr = result.get(-9)
  val flags         = result.tail(9).asTypeOf(new OpFlags)

  val funct = Mux(instType === U, 0.U, inst(14, 12))
  val imm   =
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

  io.result.valid := valid
  io.result.src1  := src1
  io.result.src2  := src2
  io.result.funct := funct
  io.result.imm   := imm
  io.result.flags := flags

  io.result.useRs1        := src1 === Src1.Reg || useRs1ForAddr
  io.result.useRs2        := src2 === Src2.Reg
  io.result.useRs1ForAddr := useRs1ForAddr
}
