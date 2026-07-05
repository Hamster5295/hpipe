package hpipe

import chisel3._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chisel3.util._
import hammer._
import hpipe.CsrAddr._

object CsrMode extends Enumeration {
  val RO, RW = Value
}
import CsrMode._

case class CsrModel(
    addr:  UInt,
    mode:  CsrMode.Value,
    data:  UInt,
    reset: UInt = 0.U,
    read:  UInt => UInt = i => i,
    write: UInt => UInt = i => i,
)

class Csr(implicit p: HPipeParameters) extends Bundle {

  val mstatus = Word()
  val mie     = Word()
  val mtvec   = Word()

  val mepc   = Word()
  val mcause = Word()
  val mtval  = Word()
  val mip    = Word()

  val cycle  = Word()
  val cycleh = Word()

  val instret  = Word()
  val instreth = Word()

  private val csrList = Seq(
// format: off
    CsrModel(MSTATUS, RW, mstatus, reset = "b1000".U),
    CsrModel(MIE,     RW, mie,     write = _ & "x888".U),
    CsrModel(MTVEC,   RW, mtvec,   write = _.head(30) ## 0.U(2.W)),
    CsrModel(MEPC,    RW, mepc,    write = _.head(30) ## 0.U(2.W)),
    CsrModel(MCAUSE,  RW, mcause),
    CsrModel(MTVAL,   RW, mtval),
    CsrModel(MIP,     RO, mip),

    CsrModel(CYCLE,    RO, cycle),
    CsrModel(INSTRET,  RO, instret),
    CsrModel(CYCLEH,   RO, cycleh),
    CsrModel(INSTRETH, RO, instreth),
// format: on
  )

  def reset(signal: Bool) = when(signal)(csrList.map(c => c.data := c.reset))

  def read(port: CsrReadPort): Unit =
    csrList.map(model =>
      when(port.addr === model.addr)(port.data := model.read(model.data)),
    )

  def write(port: CsrWritePort): Unit =
    csrList.map(model =>
      if (model.mode == RW)
        when(port.addr === model.addr)(model.data := port.data),
    )

  def transform(port: CsrTransformPort): Unit =
    csrList.map(model =>
      if (model.mode == RW)
        when(port.addr === model.addr)(port.result := model.write(port.data)),
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

class CsrTransformPort(implicit p: HPipeParameters) extends Bundle {
  val addr   = Input(CsrAddr())
  val data   = Input(Word())
  val result = Output(Word())
}

class CsrFileIO(implicit val p: HPipeParameters) extends Bundle {
  val reads      = Vec(1, new CsrReadPort)
  val writes     = Vec(1, new CsrWritePort)
  val transforms = Vec(1, new CsrTransformPort)

  val interrupt = Input(new InterruptSource)

  val csr = Output(new Csr)

  // CSR Specific
  val retire = Input(new RetireInfo)
}

class CsrFile(implicit val p: HPipeParameters) extends Module {
  val io = IO(new CsrFileIO)

  val csr = RegZero(new Csr)
  csr.reset(reset.asBool)

  io.reads.map(i => i.data := 0.U)
  io.transforms.map(i => i.result := i.data)

  io.reads.map(csr.read(_))
  io.writes.map(csr.write(_))
  io.transforms.map(csr.transform(_))

  // Passthrough
  io.reads.map(r =>
    io.writes.map(w => when(r.addr === w.addr)(r.data := w.data)),
  )

  io.csr := csr

  /// CSR Specific

  // mip
  val intr = io.interrupt
  csr.mip :=
    csr.mip | (intr.external << 11) | (intr.timer << 7) | (intr.software << 3)

  // mstatus
  csr.mstatus := Mux(
    io.retire.trapValid,
    "b1_1000".U ## csr.mstatus(3) ## "b000_0000".U, // MPIE = previous MIE
    csr.mstatus,
  )

  // mepc
  csr.mepc := Mux(io.retire.trapValid, io.retire.pc, csr.mepc)

  // mcause
  csr.mcause :=
    Mux(
      io.retire.trapValid,
      io.retire.trap.cause,
      csr.mcause,
    ) // 11 - ecall from M mode

  // cycle
  val cycleLong = (csr.cycleh ## csr.cycle) +% 1.U
  csr.cycle  := cycleLong.end(32)
  csr.cycleh := cycleLong.head(32)

  // instret
  val instretLong = (csr.instreth ## csr.instret) +% 1.U
  csr.instret  := Mux(io.retire.valid, instretLong.end(32), csr.instret)
  csr.instreth := Mux(io.retire.valid, instretLong.head(32), csr.instreth)
}
