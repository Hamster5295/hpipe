package hpipe

import chisel3._
import chisel3.util._
import hammer._

class RegFileReadPort(implicit val p: HPipeParameters) extends Bundle {
  val addr = Input(XRegAddr())
  val data = Output(Word())
}

class RegFileWritePort(implicit val p: HPipeParameters) extends Bundle {
  val addr = Input(XRegAddr())
  val data = Input(Word())
}

class RegFileIO(implicit val p: HPipeParameters) extends Bundle {
  val reads  = Vec(2, new RegFileReadPort)
  val writes = new RegFileWritePort

  val regs = Output(Vec(p.XLEN - 1, Word()))
}

class RegFile(implicit val p: HPipeParameters) extends Module {
  val io   = IO(new RegFileIO)
  val regs = RegZero(Vec(p.XLEN - 1, Word()))

  io.regs := regs

  io.reads.map(r => r.data := 0.U)

  regs.zipWithIndex.map { case (reg, idx) =>
    val addr = (idx + 1).U
    io.reads.map(r => when(r.addr === addr)(r.data := reg))
    reg := Mux(io.writes.addr === addr, io.writes.data, reg)
  }

  io.reads.map(r => when(r.addr === io.writes.addr)(r.data := io.writes.data))
  io.reads.map(r => when(!r.addr.orR)(r.data := 0.U))
}
