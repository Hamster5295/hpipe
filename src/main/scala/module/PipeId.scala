package hpipe

import chisel3._
import chisel3.util._
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

  // Decode
  val decoder = Module(new Decoder)
  decoder.io.inst := inst

  val decode = decoder.io.result

  toEx.valid := io.fromIf.valid
  toEx.flags := decode.flags
  // Regs & Imm
  toEx.rs1   := rs1Addr
  toEx.rs2   := rs2Addr
  toEx.rd    := rdAddr
  toEx.funct := decode.funct

  // RegFile
  io.rs1Read.addr := toEx.rs1
  io.rs2Read.addr := toEx.rs2

  // Feed Forward
  val ffEx  = io.fromEx
  val ffMem = io.fromMem

  val useRs1 = decode.useRs1 && rs1Addr.orR
  val rs1    = MuxIf(
    (ffEx.gprMatch(rs1Addr) && useRs1)  -> ffEx.gpr.bits.data,
    (ffMem.gprMatch(rs1Addr) && useRs1) -> ffMem.gpr.bits.data,
  )(io.rs1Read.data)

  val useRs2 = decode.useRs2 && rs2Addr.orR
  val rs2    = MuxIf(
    (ffEx.gprMatch(rs2Addr) && useRs2)  -> ffEx.gpr.bits.data,
    (ffMem.gprMatch(rs2Addr) && useRs2) -> ffMem.gpr.bits.data,
  )(io.rs2Read.data)

  val ldUseStall = ffEx.gpr.bits.isLd &&
    ((useRs1 && ffEx.gprMatch(rs1Addr)) ||
      (useRs2 && ffEx.gprMatch(rs2Addr)))

  // Operator selection
  toEx.src1 := MuxLookup(decode.src1, 0.U)(
    Seq(
      Src1.Reg -> rs1,
      Src1.Imm -> rs1Addr, // Currently only izcsr uses rs1 as imm
      Src1.PC  -> io.fromIf.pc,
    ),
  )
  toEx.src2 := MuxLookup(decode.src2, 0.U)(
    Seq(
      Src2.Reg  -> rs2,
      Src2.Imm  -> decode.imm,
      Src2.Four -> 4.U,
    ),
  )

  // Addr Gen
  toEx.addr := Mux(decode.useRs1ForAddr, rs1, io.fromIf.pc) +% decode.imm

  // Csr
  val csrAddr = Mux(decode.flags.mret, CsrAddr.MSTATUS, inst.head(12))
  io.csrRead.addr := csrAddr

  val csrSrc = MuxIf(
    ffEx.csrMatch(csrAddr)  -> ffEx.csr.bits.data,
    ffMem.csrMatch(csrAddr) -> ffMem.csr.bits.data,
  )(io.csrRead.data)

  toEx.csrAddr := csrAddr
  toEx.csrSrc  := csrSrc

  // Exception
  val excp = io.toEx.exception

  val ecall       = decode.flags.ecall
  val invalidInst = !decode.valid
  val hasExcp     = ecall || invalidInst

  excp.valid := hasExcp
  excp.cause := Mux1H(Seq(ecall -> 13.U, invalidInst -> 2.U, !hasExcp -> 0.U))

  // Valid & Ready
  io.busy := ldUseStall

  // Feed forward to IF (BTB)
  val ff = io.feedForward
  ff.gpr.valid     := decode.flags.writeRd
  ff.gpr.bits.addr := toEx.rd
  ff.gpr.bits.data := DontCare
  ff.gpr.bits.isLd := DontCare

  ff.csr.valid     := decode.flags.csr
  ff.csr.bits.addr := toEx.csrAddr
  ff.csr.bits.data := DontCare
}
