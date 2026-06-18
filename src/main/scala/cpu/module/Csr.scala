package hpipe

import chisel3._
import chisel3.util._
import hammer._

object CsrMode extends Enumeration {
  val RO, RW = Value
}
import CsrMode._

case class CsrModel(
    addr:  String,
    mode:  CsrMode.Value,
    data:  UInt,
    reset: UInt = 0.U,
    read:  (UInt, UInt) => Unit = (port, data) => port := data,
    write: (UInt, UInt) => Unit = (port, data) => data := port,
)

class Csr(implicit p: HPipeParameters) extends Bundle {

  val mstatus = Word()
//   val mie     = Word()
  val mtvec = Word()

  val mepc   = Word()
  val mcause = Word()
  val mtval  = Word()

  val cycle  = Word()
  val cycleh = Word()

  val instret  = Word()
  val instreth = Word()

  private val csrList = Seq(
    CsrModel("x300", RW, mstatus, reset = "b1000".U),
    // CsrModel("x304", RW, mie),
    CsrModel("x305", RW, mtvec, write = (p, d) => d := p.head(30) ## 0.U(2.W)),
    CsrModel("x341", RW, mepc, write = (p, d) => d := p.head(30)),
    CsrModel("x342", RW, mcause),
    CsrModel("x343", RW, mtval),

    CsrModel("xC00", RO, cycle),
    CsrModel("xC02", RO, instret),
    CsrModel("xC80", RO, cycleh),
    CsrModel("xC82", RO, instreth),
  )

  def reset() = csrList.map(model => model.data := model.reset)

  def read(port: CsrReadPort): Unit =
    csrList.map(model =>
      when(port.addr === model.addr.U)(model.read(port.data, model.data)),
    )

  def write(port: CsrWritePort): Unit =
    csrList.map(model =>
      if (model.mode == RW)
        when(port.addr === model.addr.U)(model.write(port.data, model.data)),
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
  val reads  = Vec(1, new CsrReadPort)
  val writes = Vec(1, new CsrWritePort)

  val csr = Output(new Csr)

  // CSR Specific
  val retire = Input(new RetireInfo)
}

class CsrFile(implicit val p: HPipeParameters) extends Module {
  val io = IO(new CsrFileIO)

  val csr = RegZero(new Csr)
  csr.reset()

  io.reads.map(i => i.data := 0.U)

  io.reads.map(csr.read(_))
  io.writes.map(csr.write(_))

  io.csr := csr

  /// CSR Specific

  // mstatus
  csr.mstatus := Mux(
    io.retire.ecall,
    "b1_1000".U ## csr.mstatus(3) ## "b000_0000".U,     // MPIE = previous MIE
    csr.mstatus,
  )

  // mepc
  csr.mepc := Mux(io.retire.ecall, io.retire.pc, csr.mepc)

  // mcause
  csr.mcause := Mux(io.retire.ecall, 11.U, csr.mcause) // 11 - ecall from M mode

  // cycle
  val cycleLong = (csr.cycleh ## csr.cycle) +% 1.U
  csr.cycle  := cycleLong.end(32)
  csr.cycleh := cycleLong.head(32)

  // instret
  val instretLong = (csr.instreth ## csr.instret) +% 1.U
  csr.instret  := Mux(io.retire.valid, instretLong.end(32), csr.instret)
  csr.instreth := Mux(io.retire.valid, instretLong.head(32), csr.instreth)
}
