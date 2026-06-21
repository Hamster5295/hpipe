package hpipe

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._
import hammer._
import hpipe.Insts._
import hpipe.InstType._

class PipeIdIO(implicit p: HPipeParameters) extends StageIO {
  val fromIf = Input(new If2IdIO)
  val toEx   = Output(new Id2ExIO)

  val fromEx  = Input(new DestInfo)
  val fromMem = Input(new DestInfo)

  val rs1Read = Flipped(new RegFileReadPort)
  val rs2Read = Flipped(new RegFileReadPort)
  val csrRead = Flipped(new CsrReadPort)

  val feedForward = Output(new DestInfo)
}

class PipeId(implicit val p: HPipeParameters) extends Module {
  val io = IO(new PipeIdIO)

  val toEx = io.toEx
  val inst = io.fromIf.inst
  toEx.pc       := io.fromIf.pc
  toEx.predInfo := io.fromIf.prediction

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
      ecall:  Boolean,
      ebreak: Boolean,
      mret:   Boolean,

      m:   Boolean,
      csr: Boolean,
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
        ++ s"${if (ecall) 1 else 0}"
        ++ s"${if (ebreak) 1 else 0}"
        ++ s"${if (mret) 1 else 0}"
        ++ s"${if (m) 1 else 0}"
        ++ s"${if (csr) 1 else 0}",
    )

    def T = true
    def F = false

  val instTable = TruthTable(
    Map(
// format: off

      // I
      LUI    -> parse(U,   Src1.None, Src2.Imm,  F, T, F, F, F, F, F, F, F, F, F, F),
      AUIPC  -> parse(U,   Src1.PC,   Src2.Imm,  F, T, F, F, F, F, F, F, F, F, F, F),
      JAL    -> parse(J,   Src1.PC,   Src2.Four, F, T, F, F, F, T, F, F, F, F, F, F),
      JALR   -> parse(I,   Src1.PC,   Src2.Four, T, T, F, F, F, T, F, F, F, F, F, F),
      BEQ    -> parse(B,   Src1.Reg,  Src2.Reg,  F, F, T, F, F, F, F, F, F, F, F, F),
      BNE    -> parse(B,   Src1.Reg,  Src2.Reg,  F, F, T, F, F, F, F, F, F, F, F, F),
      BLT    -> parse(B,   Src1.Reg,  Src2.Reg,  F, F, T, F, F, F, F, F, F, F, F, F),
      BGE    -> parse(B,   Src1.Reg,  Src2.Reg,  F, F, T, F, F, F, F, F, F, F, F, F),
      BLTU   -> parse(B,   Src1.Reg,  Src2.Reg,  F, F, T, F, F, F, F, F, F, F, F, F),
      BGEU   -> parse(B,   Src1.Reg,  Src2.Reg,  F, F, T, F, F, F, F, F, F, F, F, F),
      LB     -> parse(I,   Src1.None, Src2.Reg,  T, T, F, T, F, F, F, F, F, F, F, F),
      LH     -> parse(I,   Src1.None, Src2.Reg,  T, T, F, T, F, F, F, F, F, F, F, F),
      LW     -> parse(I,   Src1.None, Src2.Reg,  T, T, F, T, F, F, F, F, F, F, F, F),
      LBU    -> parse(I,   Src1.None, Src2.Reg,  T, T, F, T, F, F, F, F, F, F, F, F),
      LHU    -> parse(I,   Src1.None, Src2.Reg,  T, T, F, T, F, F, F, F, F, F, F, F),
      SB     -> parse(S,   Src1.None, Src2.Reg,  T, F, F, F, T, F, F, F, F, F, F, F),
      SH     -> parse(S,   Src1.None, Src2.Reg,  T, F, F, F, T, F, F, F, F, F, F, F),
      SW     -> parse(S,   Src1.None, Src2.Reg,  T, F, F, F, T, F, F, F, F, F, F, F),
      ADDI   -> parse(I,   Src1.Reg,  Src2.Imm,  F, T, F, F, F, F, F, F, F, F, F, F),
      SLTI   -> parse(I,   Src1.Reg,  Src2.Imm,  F, T, F, F, F, F, F, F, F, F, F, F),
      SLTIU  -> parse(I,   Src1.Reg,  Src2.Imm,  F, T, F, F, F, F, F, F, F, F, F, F),
      XORI   -> parse(I,   Src1.Reg,  Src2.Imm,  F, T, F, F, F, F, F, F, F, F, F, F),
      ORI    -> parse(I,   Src1.Reg,  Src2.Imm,  F, T, F, F, F, F, F, F, F, F, F, F),
      ANDI   -> parse(I,   Src1.Reg,  Src2.Imm,  F, T, F, F, F, F, F, F, F, F, F, F),
      SLLI   -> parse(I,   Src1.Reg,  Src2.Imm,  F, T, F, F, F, F, F, F, F, F, F, F),
      SRLI   -> parse(I,   Src1.Reg,  Src2.Imm,  F, T, F, F, F, F, F, F, F, F, F, F),
      SRAI   -> parse(I,   Src1.Reg,  Src2.Imm,  F, T, F, F, F, F, T, F, F, F, F, F),
      ADD    -> parse(R,   Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, F, F, F, F),
      SUB    -> parse(R,   Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, T, F, F, F, F, F),
      SLL    -> parse(R,   Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, F, F, F, F),
      SLT    -> parse(R,   Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, F, F, F, F),
      SLTU   -> parse(R,   Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, F, F, F, F),
      XOR    -> parse(R,   Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, F, F, F, F),
      SRL    -> parse(R,   Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, F, F, F, F),
      SRA    -> parse(R,   Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, T, F, F, F, F, F),
      OR     -> parse(R,   Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, F, F, F, F),
      AND    -> parse(R,   Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, F, F, F, F),
      ECALL  -> parse(N,   Src1.None, Src2.None, F, F, F, F, F, F, F, T, F, F, F, F),
      EBREAK -> parse(N,   Src1.None, Src2.None, F, F, F, F, F, F, F, F, T, F, F, F),

      // M Extension
      MUL    -> parse(R,   Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, F, F, T, F),
      MULH   -> parse(R,   Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, F, F, T, F),
      MULHSU -> parse(R,   Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, F, F, T, F),
      MULHU  -> parse(R,   Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, F, F, T, F),
      DIV    -> parse(R,   Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, F, F, T, F),
      DIVU   -> parse(R,   Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, F, F, T, F),
      REM    -> parse(R,   Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, F, F, T, F),
      REMU   -> parse(R,   Src1.Reg,  Src2.Reg,  F, T, F, F, F, F, F, F, F, F, T, F),

      // Zicsr
      CSRRW  -> parse(Csr, Src1.Reg,  Src2.None, F, T, F, F, F, F, F, F, F, F, F, T),
      CSRRS  -> parse(Csr, Src1.Reg,  Src2.None, F, T, F, F, F, F, F, F, F, F, F, T),
      CSRRC  -> parse(Csr, Src1.None, Src2.None, F, T, F, F, F, F, F, F, F, F, F, T),
      CSRRWI -> parse(Csr, Src1.Imm,  Src2.None, F, T, F, F, F, F, F, F, F, F, F, T),
      CSRRSI -> parse(Csr, Src1.Imm,  Src2.None, F, T, F, F, F, F, F, F, F, F, F, T),
      CSRRCI -> parse(Csr, Src1.None, Src2.None, F, T, F, F, F, F, F, F, F, F, F, T),

      // Trap Return
      MRET   -> parse(N,   Src1.None, Src2.None, F, F, F, F, F, F, F, F, F, T, F, T),

// format: on
    ),
    BitPat.N(19),
  )
  val result = decoder(inst, instTable)

  val valid    = result.msb()
  val instType = result.get(-2, -4).asTypeOf(InstType())
  val src1     = Src1.safe(result.get(-5, -6))._1
  val src2     = Src2.safe(result.get(-7, -8))._1
  val addrType = result.get(-9)
  val flags    = result.tail(9).asTypeOf(new OpFlags)

  toEx.valid := io.fromIf.valid
  toEx.flags := flags
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
  val ffEx  = io.fromEx
  val ffMem = io.fromMem

  val useRs1 = src1 === Src1.Reg || addrType
  val rs1    = MuxIf(
    (ffEx.gprMatch(rs1Addr) && useRs1)  -> ffEx.gpr.bits.data,
    (ffMem.gprMatch(rs1Addr) && useRs1) -> ffMem.gpr.bits.data,
  )(io.rs1Read.data)

  dontTouch(rs1Addr)

  val useRs2 = (src2 === Src2.Reg) && rs2Addr.orR
  val rs2    = MuxIf(
    (ffEx.gprMatch(rs2Addr) && useRs2)  -> ffEx.gpr.bits.data,
    (ffMem.gprMatch(rs2Addr) && useRs2) -> ffMem.gpr.bits.data,
  )(io.rs2Read.data)

  val ldUseStall = ffEx.gpr.bits.isLd &&
    ((useRs1 && ffEx.gprMatch(rs1Addr)) ||
      (useRs2 && ffEx.gprMatch(rs2Addr)))

  // Operator selection
  toEx.src1 := MuxLookup(src1, 0.U)(
    Seq(
      Src1.Reg -> rs1,
      Src1.Imm -> rs1Addr, // Currently only izcsr uses rs1 as imm
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

  // Csr
  val csrAddr = Mux(flags.mret, CsrAddr.MSTATUS, inst.head(12))
  io.csrRead.addr := csrAddr

  val csrSrc = MuxIf(
    ffEx.csrMatch(csrAddr)  -> ffEx.csr.bits.data,
    ffMem.csrMatch(csrAddr) -> ffMem.csr.bits.data,
  )(io.csrRead.data)

  toEx.csrAddr := csrAddr
  toEx.csrSrc  := csrSrc

  // Exception
  val excp = io.toEx.exception

  val ecall       = flags.ecall
  val invalidInst = !valid
  val hasExcp     = ecall || invalidInst

  excp.valid := hasExcp
  excp.cause := Mux1H(Seq(ecall -> 13.U, invalidInst -> 2.U, !hasExcp -> 0.U))

  // Valid & Ready
  io.busy := ldUseStall

  // Feed forward to IF (BTB)
  val ff = io.feedForward
  ff.gpr.valid     := flags.writeRd
  ff.gpr.bits.addr := toEx.rd
  ff.gpr.bits.data := DontCare
  ff.gpr.bits.isLd := DontCare

  ff.csr.valid     := flags.csr
  ff.csr.bits.addr := toEx.csrAddr
  ff.csr.bits.data := DontCare
}
