package hpipe

import chisel3._
import chisel3.util._
import hammer._

class HistoryTableIO(implicit p: HPipeParameters) extends Bundle {
  val pc   = Input(Addr())
  val take = Output(Bool())

  val writePc     = Input(Addr())
  val writeEnable = Input(Bool())
  val writeTake   = Input(Bool())
}

class HistoryTable(implicit p: HPipeParameters) extends Module {
  val tagWidth = log2Ceil(p.HistTable.Size)

  val io         = IO(new HistoryTableIO)
  val query      = io.pc.end(tagWidth)
  val writeQuery = io.writePc.end(tagWidth)

  val entries = VecInit(Seq.tabulate(p.HistTable.Size) { i =>
    val cnter = Module(SaturateCounter(p.HistTable.RecordWidth, 1))
    cnter.io.enable   := io.writeEnable && writeQuery === i.U
    cnter.io.op       := io.writeTake
    cnter.io.set      := false.B
    cnter.io.setValue := 0.U
    cnter.io.value
  })

  io.take := entries(query).msb()
}
