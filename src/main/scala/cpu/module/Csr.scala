package hpipe

import chisel3._
import chisel3.util._
import hammer._

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

  val cycle  = Word()
  val cycleh = Word()

  val instret  = Word()
  val instreth = Word()

  private val csrList = Seq(
    CsrModel("x300", RW, mstatus),
    CsrModel("x304", RW, mie),
    CsrModel("x341", RW, mepc),
    CsrModel("x342", RW, mcause),
    CsrModel("x343", RW, mtval),

    CsrModel("xC00", RO, cycle),
    CsrModel("xC02", RO, instret),
    CsrModel("xC80", RO, cycleh),
    CsrModel("xC82", RO, instreth),
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

  // CSR Specific
  val retireValid = Input(Bool())
}

class CsrFile(implicit val p: HPipeParameters) extends Module {
  val io = IO(new CsrFileIO)

  val csr = RegZero(new Csr)

  io.read.map(i => i.data := 0.U)

  io.read.map(csr.read(_))
  io.write.map(csr.write(_))

  io.csr := csr

  // CSR Specific
  val cycleLong = (csr.cycleh ## csr.cycle) +% 1.U
  csr.cycle  := cycleLong.end(32)
  csr.cycleh := cycleLong.head(32)

  val instretLong = (csr.instreth ## csr.instret) +% 1.U
  csr.instret  := Mux(io.retireValid, instretLong.end(32), csr.instret)
  csr.instreth := Mux(io.retireValid, instretLong.head(32), csr.instreth)
}
