package hpipe

import chisel3._
import chisel3.util._
import hammer._

class RegFileReadPort(implicit val p: Parameters) extends Bundle {
  val addr = Input(XRegAddr())
  val data = Output(Word())
}

class RegFileWritePort(implicit val p: Parameters) extends Bundle {
  val addr = Input(XRegAddr())
  val data = Input(Word())
}

class RegFileIO(implicit val p: Parameters) extends Bundle {
  val read  = Vec(3, new RegFileReadPort)
  val write = new RegFileWritePort

  val regs = Output(Vec(p.XLEN - 1, Word()))
}

class RegFile(implicit val p: Parameters) extends Module {
  val io   = IO(new RegFileIO)
  val regs = RegZero(Vec(p.XLEN - 1, Word()))

  io.regs := regs
  io.read.map(i => i.data := 0.U)

  val passthroughs = io.read.map(i => (i, i.addr === io.write.addr)).toMap

  io.read.map { i =>
    when(i.addr === 0.U) {
      i.data := 0.U
    }
  }

  regs.zipWithIndex.map { case (reg, idx) =>
    val addr = (idx + 1).U
    io.read.map { i =>
      when(i.addr === addr) {
        i.data := Mux(passthroughs(i), io.write.data, reg)
      }
    }
    when(io.write.addr === addr) {
      reg := io.write.data
    }
  }
}
