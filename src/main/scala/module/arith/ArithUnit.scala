package hpipe

import chisel3._
import chisel3.util._
import hammer._
import hpipe.ALUOp._

class ArithUnitIO(implicit val p: HPipeParameters) extends Bundle {
  val src1 = Input(Word())
  val src2 = Input(Word())
  val op   = Input(ALUOp())

  val inv = Input(Bool()) // For SRL/SRA only

  val result = Output(Word())
}

class ArithUnit(implicit val p: HPipeParameters) extends Module {
  val io = IO(new ArithUnitIO)

  val unsigned = io.op === SLTU

  // Sign ext
  val src1 = Mux(unsigned, 0.B, io.src1.msb()) ## io.src1
  val src2 = Mux(unsigned, 0.B, io.src2.msb()) ## io.src2

  val sub = MuxLookup(io.op, false.B)(
    Seq(
      ADD  -> io.inv,
      SLT  -> true.B,
      SLTU -> true.B,
    ),
  )

  val add =
    if (p.UseArithMacro) (SignExt(src1, 33) + SignExt(Mux(sub, ~src2, src2), 33)).end(33)
    else UIntCLA(33)(src1, Mux(sub, ~src2, src2), sub).end(33)

  val sll = io.src1 << io.src2.end(5)
  val srl = io.src1 >> io.src2.end(5)
  val sra = (Fill(32, src1.msb(1)) ## io.src1) >> io.src2.end(5)
  val and = src1 & src2
  val or  = src1 | src2
  val xor = src1 ^ src2

  io.result := MuxLookup(io.op, 0.U)(
    Seq(
      ADD  -> add,
      SLL  -> sll.end(32),
      SLT  -> add.msb(),
      SLTU -> add.msb(),
      XOR  -> xor,
      SRX  -> Mux(io.inv, sra, srl),
      OR   -> or,
      AND  -> and,
    ),
  )
}
