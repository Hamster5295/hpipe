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
  val read  = Vec(3, new RegFileReadPort)
  val write = new RegFileWritePort

  val regs = Output(Vec(p.XLEN - 1, Word()))
}

class RegFile(implicit val p: HPipeParameters) extends Module {
  val io   = IO(new RegFileIO)
  val regs = RegZero(Vec(p.XLEN - 1, Word()))

  io.regs := regs

  io.read.map(r => r.data := 0.U)

  regs.zipWithIndex.map { case (reg, idx) =>
    val addr = (idx + 1).U
    io.read.map(r => when(r.addr === addr)(r.data := reg))
    reg := Mux(io.write.addr === addr, io.write.data, reg)
  }

  io.read.map(r => when(r.addr === io.write.addr)(r.data := io.write.data))
  io.read.map(r => when(!r.addr.orR)(r.data := 0.U))

//   io.read.map(i => i.data := 0.U)
//   io.read.map { i =>
//     when(i.addr === 0.U) {
//       i.data := 0.U
//     }
//   }

//   regs.zipWithIndex.map { case (reg, idx) =>
//     val addr = (idx + 1).U
//     io.read.map { i =>
//       when(i.addr === addr) {
//         i.data := Mux(passthroughs(i), io.write.data, reg)
//       }
//     }
//     when(io.write.addr === addr) {
//       reg := io.write.data
//     }
//   }
}
