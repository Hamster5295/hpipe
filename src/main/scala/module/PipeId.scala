package hpipe

import chisel3._
import chisel3.util._
import hammer._
import hpipe.Insts._
import hpipe.InstType._

class PipeIdIO(implicit p: HPipeParameters) extends StageIO {
  val fromIf = Input(new If2IdIO)
//   val toEx   = Output(new Id2ExIO)
  val toSg = Output(new Id2SgIO)

  val feedForward = Output(new DestInfo)
}

class PipeId(implicit val p: HPipeParameters) extends Module {
  val io = IO(new PipeIdIO)

  val toSg = io.toSg
  val inst = io.fromIf.inst
  toSg.pc       := io.fromIf.pc
  toSg.predInfo := io.fromIf.prediction

  val rs1Addr = inst(19, 15)
  val rs2Addr = inst(24, 20)
  val rdAddr  = inst(11, 7)

  // Decode
  val decoder = Module(new Decoder)
  decoder.io.inst := inst

  val decoded = decoder.io.result

  toSg.valid := io.fromIf.valid

  // Regs & Imm
  toSg.rs1Addr     := rs1Addr
  toSg.rs2Addr     := rs2Addr
  toSg.rdAddr      := rdAddr
  toSg.decoded := decoded

  toSg.decoded.useRs1 := decoded.useRs1 && rs1Addr.orR
  toSg.decoded.useRs2 := decoded.useRs2 && rs2Addr.orR

  // Csr
  toSg.csrAddr := Mux(decoded.flags.mret, CsrAddr.MSTATUS, inst.head(12))

  // Exception
  val excp = toSg.exception

  val ecall       = decoded.flags.ecall
  val invalidInst = !decoded.valid
  val hasExcp     = ecall || invalidInst

  excp.valid := hasExcp
  excp.cause := Mux1H(Seq(ecall -> 13.U, invalidInst -> 2.U, !hasExcp -> 0.U))

  // Valid & Ready
  io.busy := false.B

  // Feed forward to IF (BTB)
  val ff = io.feedForward
  ff.gpr.valid     := decoded.flags.writeRd
  ff.gpr.bits.addr := toSg.rdAddr
  ff.gpr.bits.data := DontCare
  ff.gpr.bits.isLd := DontCare

  ff.csr.valid     := decoded.flags.csr
  ff.csr.bits.addr := toSg.csrAddr
  ff.csr.bits.data := DontCare
}
