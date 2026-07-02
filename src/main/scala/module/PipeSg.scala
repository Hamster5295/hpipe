package hpipe

import chisel3._
import chisel3.util._
import hammer._

class PipeSgIO(implicit p: HPipeParameters) extends StageIO {
  val fromId = Input(new Id2SgIO)
  val toEx   = Output(new Sg2ExIO)

  val feedForwardEx  = Input(new DestInfo)
  val feedForwardMem = Input(new DestInfo)

  val rs1Read = Flipped(new RegFileReadPort)
  val rs2Read = Flipped(new RegFileReadPort)
  val csrRead = Flipped(new CsrReadPort)

  val feedForward = Output(new DestInfo)
}

class PipeSg(implicit p: HPipeParameters) extends Module {
  val io = IO(new PipeSgIO)

  val fromId  = io.fromId
  val toEx    = io.toEx
  val decoded = fromId.decoded

  val ffEx  = io.feedForwardEx
  val ffMem = io.feedForwardMem

  io.rs1Read.addr := fromId.rs1Addr
  io.rs2Read.addr := fromId.rs2Addr
  io.csrRead.addr := fromId.csrAddr

  val rs1 = MuxIf(
    (ffEx.gprMatch(fromId.rs1Addr) && decoded.useRs1)  -> ffEx.gpr.bits.data,
    (ffMem.gprMatch(fromId.rs1Addr) && decoded.useRs1) -> ffMem.gpr.bits.data,
  )(io.rs1Read.data)

  val rs2 = MuxIf(
    (ffEx.gprMatch(fromId.rs2Addr) && decoded.useRs2)  -> ffEx.gpr.bits.data,
    (ffMem.gprMatch(fromId.rs2Addr) && decoded.useRs2) -> ffMem.gpr.bits.data,
  )(io.rs2Read.data)

  val csrSrc = MuxIf(
    ffEx.csrMatch(fromId.csrAddr)  -> ffEx.csr.bits.data,
    ffMem.csrMatch(fromId.csrAddr) -> ffMem.csr.bits.data,
  )(io.csrRead.data)

  val ldUseStall = ffEx.gpr.bits.isLd &&
    ((decoded.useRs1 && ffEx.gprMatch(fromId.rs1Addr)) ||
      (decoded.useRs2 && ffEx.gprMatch(fromId.rs2Addr)))

  toEx.valid   := fromId.valid
  toEx.pc      := fromId.pc
  toEx.rs1Addr := fromId.rs1Addr
  toEx.rs2Addr := fromId.rs2Addr
  toEx.rdAddr  := fromId.rdAddr
  toEx.csrAddr := fromId.csrAddr

  toEx.src1 := MuxLookup(decoded.src1, 0.U)(
    Seq(
      Src1.Reg -> rs1,
      Src1.Imm -> fromId.rs1Addr, // Currently only izcsr uses rs1 as imm
      Src1.PC  -> fromId.pc,
    ),
  )
  toEx.src2 := MuxLookup(decoded.src2, 0.U)(
    Seq(
      Src2.Reg  -> rs2,
      Src2.Imm  -> decoded.imm,
      Src2.Four -> 4.U,
    ),
  )
  toEx.csrSrc := csrSrc

  toEx.addrBase := Mux(decoded.useRs1ForAddr, rs1, fromId.pc)
  toEx.imm      := decoded.imm

  toEx.funct     := decoded.funct
  toEx.flags     := decoded.flags
  toEx.exception := fromId.exception
  toEx.predInfo  := fromId.predInfo

  // Pipeline handshake
  io.busy := ldUseStall

  // Feed forward to IF (BTB)
  val ff = io.feedForward
  ff.gpr.valid     := decoded.flags.writeRd
  ff.gpr.bits.addr := fromId.rdAddr
  ff.gpr.bits.data := DontCare
  ff.gpr.bits.isLd := DontCare

  ff.csr.valid     := decoded.flags.csr
  ff.csr.bits.addr := fromId.csrAddr
  ff.csr.bits.data := DontCare
}
