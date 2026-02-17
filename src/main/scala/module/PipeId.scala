package hpipe

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._
import hammer._
import hpipe.Insts._
import hpipe.InstType._

class PipeIdIO(implicit val p: Parameters) extends Bundle {
  val fromIf = Flipped(new If2IdIO)
  val toEx   = new Id2ExIO

  val fromEx  = Input(new FeedForward)
  val fromMem = Input(new FeedForward)

  val fromWb = new RegFileWritePort
}

class PipeId(implicit val p: Parameters) extends Module {
  val io = IO(new PipeIdIO)

  val toEx = io.toEx
  val inst = io.fromIf.inst
  toEx.pc := io.fromIf.pc

  // UOp
  def parse(
      instType: InstType.Type,
      rs1:      Src1.Type,
      rs2:      Src2.Type,
      addrType: Boolean,

      rd:  Boolean,
      br:  Boolean,
      ld:  Boolean,
      st:  Boolean,
      jal: Boolean,
      sra: Boolean
  ) =
    BitPat(
      s"b${instType.asUInt.toBin(3)}"
        ++ rs1.asUInt.toBin(2)
        ++ rs2.asUInt.toBin(2)
        ++ s"${if (addrType) 1 else 0}"
        ++ s"${if (rd) 1 else 0}"
        ++ s"${if (br) 1 else 0}"
        ++ s"${if (ld) 1 else 0}"
        ++ s"${if (st) 1 else 0}"
        ++ s"${if (jal) 1 else 0}"
        ++ s"${if (sra) 1 else 0}"
    )

  val instTable = TruthTable(
    Map(
      LUI    -> parse(U, Src1.None, Src2.Imm, false, true, false, false, false, false, false),
      AUIPC  -> parse(U, Src1.PC, Src2.Imm, false, true, false, false, false, false, false),
      JAL    -> parse(J, Src1.PC, Src2.Four, false, true, false, false, false, true, false),
      JALR   -> parse(I, Src1.PC, Src2.Four, true, true, false, false, false, true, false),
      BEQ    -> parse(B, Src1.Reg, Src2.Reg, false, false, true, false, false, false, false),
      BNE    -> parse(B, Src1.Reg, Src2.Reg, false, false, true, false, false, false, false),
      BLT    -> parse(B, Src1.Reg, Src2.Reg, false, false, true, false, false, false, false),
      BGE    -> parse(B, Src1.Reg, Src2.Reg, false, false, true, false, false, false, false),
      BLTU   -> parse(B, Src1.Reg, Src2.Reg, false, false, true, false, false, false, false),
      BGEU   -> parse(B, Src1.Reg, Src2.Reg, false, false, true, false, false, false, false),
      LB     -> parse(I, Src1.None, Src2.Reg, true, true, false, true, false, false, false),
      LH     -> parse(I, Src1.None, Src2.Reg, true, true, false, true, false, false, false),
      LW     -> parse(I, Src1.None, Src2.Reg, true, true, false, true, false, false, false),
      LBU    -> parse(I, Src1.None, Src2.Reg, true, true, false, true, false, false, false),
      LHU    -> parse(I, Src1.None, Src2.Reg, true, true, false, true, false, false, false),
      SB     -> parse(S, Src1.None, Src2.Reg, true, false, false, false, true, false, false),
      SH     -> parse(S, Src1.None, Src2.Reg, true, false, false, false, true, false, false),
      SW     -> parse(S, Src1.None, Src2.Reg, true, false, false, false, true, false, false),
      ADDI   -> parse(I, Src1.Reg, Src2.Imm, false, true, false, false, false, false, false),
      SLTI   -> parse(I, Src1.Reg, Src2.Imm, false, true, false, false, false, false, false),
      SLTIU  -> parse(I, Src1.Reg, Src2.Imm, false, true, false, false, false, false, false),
      XORI   -> parse(I, Src1.Reg, Src2.Imm, false, true, false, false, false, false, false),
      ORI    -> parse(I, Src1.Reg, Src2.Imm, false, true, false, false, false, false, false),
      ANDI   -> parse(I, Src1.Reg, Src2.Imm, false, true, false, false, false, false, false),
      SLLI   -> parse(I, Src1.Reg, Src2.Imm, false, true, false, false, false, false, false),
      SRLI   -> parse(I, Src1.Reg, Src2.Imm, false, true, false, false, false, false, false),
      SRAI   -> parse(I, Src1.Reg, Src2.Imm, false, true, false, false, false, false, true),
      ADD    -> parse(R, Src1.Reg, Src2.Reg, false, true, false, false, false, false, false),
      SUB    -> parse(R, Src1.Reg, Src2.Reg, false, true, false, false, false, false, false),
      SLL    -> parse(R, Src1.Reg, Src2.Reg, false, true, false, false, false, false, false),
      SLT    -> parse(R, Src1.Reg, Src2.Reg, false, true, false, false, false, false, false),
      SLTU   -> parse(R, Src1.Reg, Src2.Reg, false, true, false, false, false, false, false),
      XOR    -> parse(R, Src1.Reg, Src2.Reg, false, true, false, false, false, false, false),
      SRL    -> parse(R, Src1.Reg, Src2.Reg, false, true, false, false, false, false, false),
      SRA    -> parse(R, Src1.Reg, Src2.Reg, false, true, false, false, false, false, true),
      OR     -> parse(R, Src1.Reg, Src2.Reg, false, true, false, false, false, false, false),
      AND    -> parse(R, Src1.Reg, Src2.Reg, false, true, false, false, false, false, false),
      ECALL  -> parse(N, Src1.None, Src2.None, false, false, false, false, false, false, false),
      EBREAK -> parse(N, Src1.None, Src2.None, false, false, false, false, false, false, false)
    ),
    BitPat(0.U(14.W))
  )
  val result   = decoder(inst, instTable)
  val instType = result.head(3).asTypeOf(InstType())
  val src1     = Src1.safe(result.get(-4, -5))._1
  val src2     = Src2.safe(result.get(-6, -7))._1
  val addrType = result.get(-8)
  toEx.uop := result.tail(8).asTypeOf(new UOp)

  // Regs & Imm
  val rs1Addr = inst(19, 15)
  val rs2Addr = inst(24, 20)
  toEx.rs1   := rs1Addr
  toEx.rs2   := rs2Addr
  toEx.rd    := inst(11, 7)
  toEx.funct := inst(14, 12)
  val imm = SignExt(
    MuxLookup(instType, 0.U(32.W))(
      Seq(
        I -> inst(31, 20),
        S -> inst(31, 25) ## inst(11, 7),
        B -> inst(31) ## inst(7) ## inst(30, 25) ## inst(11, 8) ## 0.U(1.W),
        U -> inst(31, 12) ## 0.U(12.W),
        J -> inst(31) ## inst(19, 12) ## inst(20) ## inst(30, 21) ## 0.U(1.W)
      )
    ),
    32
  )

  // RegFile
  val regFile = Module(new RegFile)
  regFile.io.readA.addr := toEx.rs1
  regFile.io.readB.addr := toEx.rs2
  regFile.io.write      := io.fromWb

  // Feed Forward
  val useRs1 = src1 === Src1.Reg
  val rs1    = MuxIf(
    (io.fromEx.isValid(rs1Addr) && useRs1)  -> io.fromEx.data,
    (io.fromMem.isValid(rs1Addr) && useRs1) -> io.fromMem.data
  )(regFile.io.readA.data)

  val useRs2 = (src2 === Src2.Reg) && rs2Addr.orR
  val rs2    = MuxIf(
    (io.fromEx.isValid(rs2Addr) && useRs2) -> io.fromEx.data,
    (io.fromEx.isValid(rs2Addr) && useRs2) -> io.fromMem.data
  )(regFile.io.readB.data)

  // Operator selection
  toEx.src1 := MuxLookup(src1, 0.U)(
    Seq(
      Src1.Reg -> rs1,
      Src1.PC  -> io.fromIf.pc
    )
  )
  toEx.src2 := MuxLookup(src2, 0.U)(
    Seq(
      Src2.Reg  -> rs2,
      Src2.Imm  -> imm,
      Src2.Four -> 4.U
    )
  )

  // Addr Gen
  toEx.addr := Mux(addrType, rs1, io.fromIf.pc) +% imm
}
