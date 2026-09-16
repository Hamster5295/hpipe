package hpipe.decode

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._
import hammer._
import hpipe._

import Insts._
import InstType._

class BranchDecodeResult(implicit p: HPipeParameters) extends Bundle {
  val isJal  = Bool()
  val isJalr = Bool()
  val isMret = Bool()
}

class BranchDecoderIO(implicit p: HPipeParameters) extends Bundle {
  val inst = Input(Inst())
  val out  = Output(new BranchDecodeResult)
}

class BranchDecoder(implicit p: HPipeParameters) extends Module {
  val io = IO(new BranchDecoderIO)

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
  io.out.isJal  := decoded.msb()
  io.out.isJalr := decoded.msb(1)
  io.out.isMret := decoded.msb(2)
}
