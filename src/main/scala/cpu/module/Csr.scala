package hpipe

import chisel3._
import chisel3.util._

object CsrMode extends Enumeration {
  val RO, RW = Value
}
import CsrMode._

case class CsrModel(addr: String, mode: CsrMode.Value, data: Data)

class Csr(implicit p: HPipeParameters) extends Bundle {

  val mstatus = Word()
  val mie     = Word()

  val mepc   = Word()
  val mcause = Word()
  val mtval  = Word()

  private val csrList = Seq(
    CsrModel("x300", RW, mstatus),
    CsrModel("x304", RW, mie),
    CsrModel("x341", RW, mepc),
    CsrModel("x342", RW, mcause),
    CsrModel("x343", RW, mtval),
  )

  def read(port: CsrReadPort): Unit =
    csrList.map(model =>
      when(port.addr === model.addr.U)(port.data := model.data),
    )

  def write(port: CsrWritePort): Unit =
    csrList.map(model =>
      if (model.mode == RW)
        when(port.addr === model.addr.U)(model.data := port.data),
    )
}

class CsrReadPort(implicit p: HPipeParameters) extends Bundle {
  val addr = Input(CsrAddr())
  val data = Output(Word())
}

class CsrWritePort(implicit p: HPipeParameters) extends Bundle {
  val addr = Input(CsrAddr())
  val data = Input(Word())
}

class CsrFileIO(implicit val p: HPipeParameters) extends Bundle {
  val read  = Vec(1, new CsrReadPort)
  val write = Vec(1, new CsrWritePort)

  val csr = Output(new Csr)
}

class CsrFile(implicit val p: HPipeParameters) extends Module {
  val io = IO(new CsrFileIO)

  val csr = Reg(new Csr)

  io.read.map(i => i.data := 0.U)

  io.read.map(csr.read(_))
  io.write.map(csr.write(_))

  io.csr := csr
}
