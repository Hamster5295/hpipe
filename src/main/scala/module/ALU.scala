package hpipe

import chisel3._
import chisel3.util._
import hammer._
import hpipe.ALUOp._

class ALUIO(implicit p: Parameters) extends Bundle {
  val src1 = Input(Word())
  val src2 = Input(Word())
  val op   = Input(ALUOp())

  val inv = Input(Bool()) // For SRL/SRA only

  val result = Output(Word())
}

class ALU(implicit p: Parameters) extends Module {
  val io = IO(new ALUIO)

  val unsigned = io.op === SLTU

  // Sign ext
  val src1 = Mux(unsigned, 0.B, io.src1.msb()) ## io.src1
  val src2 = Mux(unsigned, 0.B, io.src2.msb()) ## io.src2

  val add = src1 +% src2
  val sub = src1 +% ~src2 +% 1.U
  val sll = io.src1 << io.src2.end(5)
  val srl = io.src1 >> io.src2.end(5)
  val sra = (Fill(32, src1.msb(1)) ## io.src1) >> io.src2.end(5)
  val and = src1 & src2
  val or  = src1 | src2
  val xor = src1 ^ src2

  io.result := MuxLookup(io.op, 0.U)(
    Seq(
      ADD  -> Mux(io.inv, sub, add).end(32),
      SLL  -> sll.end(32),
      SLT  -> sub.msb(),
      SLTU -> sub.msb(),
      XOR  -> xor,
      SRX  -> Mux(io.inv, sra, srl),
      OR   -> or,
      AND  -> and
    )
  )
}
