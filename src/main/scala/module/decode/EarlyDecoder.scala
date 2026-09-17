package hpipe.decode

import Insts._
import InstType._
import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._
import hammer._
import hpipe._

class EarlyDecodeResult(implicit p: HPipeParameters) extends Bundle {
  val isUncond   = Bool()
  val uncondAddr = Addr()

  val isCall = Bool()
  val isRet  = Bool()
  val isMret = Bool()
}

class EarlyDecoderIO(implicit p: HPipeParameters) extends Bundle {
  val pc   = Input(Addr())
  val inst = Input(Inst())
  val out  = Output(new EarlyDecodeResult)
}

class EarlyDecoder(implicit p: HPipeParameters) extends Module {
  val io = IO(new EarlyDecoderIO)

  val pc   = io.pc
  val inst = io.inst
  val out  = io.out

  def parse(
      jal:  Boolean,
      jalr: Boolean,
      mret: Boolean,
  ) =
    BitPat(
      s"b${if (jal) 1 else 0}"
        ++ s"${if (jalr) 1 else 0}"
        ++ s"${if (mret) 1 else 0}",
    )

  val table = TruthTable(
    Map(
      JAL  -> parse(true, false, false),
      JALR -> parse(false, true, false),
      MRET -> parse(false, false, true),
    ),
    BitPat.N(3),
  )
  val decoded = decoder(io.inst, table)
  val isJal   = decoded.msb()
  val isJalr  = decoded.msb(1)
  val isMret  = decoded.msb(2)

  val imm = SignExt(
    inst(31) ## inst(19, 12) ## inst(20) ## inst(30, 21) ## 0.U(1.W),
    32,
  )

  val rs1Addr = inst(19, 15)
  val rdAddr  = inst(11, 7)

  val isCall = (isJal || isJalr) && (rdAddr === 1.U || rdAddr === 5.U)
  val isRet  = isJalr && !(rs1Addr === rdAddr) &&
    (rs1Addr === 1.U || rs1Addr === 5.U) && !inst(31, 20).orR

  out.isUncond   := isJal
  out.uncondAddr := UIntAdd(32, pc, imm)
  out.isCall     := isCall
  out.isRet      := isRet
  out.isMret     := isMret

//   if (p.ExtC) {
//     val isJalC  = InstCs.C_J.matches(inst) || InstCs.C_JAL.matches(inst)
//     val isJalrC = InstCs.C_JALR.matches(inst) || InstCs.C_JR.matches(inst)
//     val immC    = SignExt(
//       inst(12) ## inst(8) ## inst(10, 9) ## inst(6) ## inst(7) ## inst(2) ##
//         inst(11) ## inst(5, 3) ## 0.U(1.W),
//       32,
//     )

//     val isCallC = InstCs.C_JAL.matches(inst)
//     val isRetC  = InstCs.C_JR.matches(inst)

//     out.isUncond   := isJal || isJalC
//     out.uncondAddr := UIntAdd(32, pc, Mux(isC, immC, imm))
//     out.isCall     := isCall || isCallC
//     out.isRet      := isRet || isRetC
//     out.isMret     := isMret
//     out.isC        := isC

//   } else {
//     out.isUncond   := isJal
//     out.uncondAddr := UIntAdd(32, pc, imm)
//     out.isCall     := isCall
//     out.isRet      := isRet
//     out.isMret     := isMret
//     out.isC        := false.B
//   }

}
