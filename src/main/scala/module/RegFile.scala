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
  val readA = new RegFileReadPort
  val readB = new RegFileReadPort
  val write = new RegFileWritePort
}

class RegFile(implicit val p: Parameters) extends Module {
  val io   = IO(new RegFileIO)
  val regs = RegZero(Vec(p.XLEN - 1, UInt(p.DataWidth.W)))

  io.readA.data := 0.U
  io.readB.data := 0.U

  val passA = io.write.addr === io.readA.addr
  val passB = io.write.addr === io.readB.addr

  when(io.readA.addr === 0.U) {
    io.readA.data := 0.U
  }
  when(io.readB.addr === 0.U) {
    io.readB.data := 0.U
  }

  regs.zipWithIndex.map { case (reg, idx) =>
    val addr = (idx + 1).U
    when(io.readA.addr === addr) {
      io.readA.data := Mux(passA, io.write.data, reg)
    }
    when(io.readB.addr === addr) {
      io.readB.data := Mux(passB, io.write.data, reg)
    }
    when(io.write.addr === addr) {
      reg := io.write.data
    }
  }
}