package hpipe

import chisel3._
import chisel3.util._
import hammer._
import hammer.model._

class HistBufferIO(implicit p: HPipeParameters) extends BranchPredictorIO

class HistBuffer(implicit val p: HPipeParameters) extends Module {
  val conf = p.HistBuf

  val io = IO(new HistBufferIO)

  // Entries
  // Is this entry valid?
  val entryValids = RegZero(Vec(conf.Count, Bool()))
  // The PC tag of the entry
  val entryTags = RegZero(Vec(conf.Count, UInt(conf.PCLen.W)))
  // The counter of the prediction
  val entryCnters = Seq.fill(conf.Count)(
    Module(SaturateCounter(
      conf.CnterWidth,
      Fixed.mask(conf.CnterWidth - 1),
    )),
  )
  val entryCnterValues = VecInit(entryCnters.map(i => i.io.value))

  // Read
  val read     = io.read
  val rmatches =
    VecInit(entryTags.map(i => i === read.pc.end(conf.PCLen)))
  val rmatched = rmatches.asUInt.orR

  val ridx = PriorityEncoder(rmatches.asUInt)
  read.brTake := read.info.isBr && rmatched && entryCnterValues(ridx).msb()
  read.target := Mux(
    read.brTake,
    read.brAddr,
    io.read.defaultTarget,
  )

  val plru = Module(new PseudoLruSelector(conf.Count))
  plru.io.hitValid := rmatched
  plru.io.hitIndex := ridx

  // Write
  val write    = io.write
  val wmatches =
    VecInit(entryTags.map(i => i === write.pc.end(conf.PCLen)))
  val wmatched = wmatches.asUInt.orR

  // We write empty entries first
  val hasEmpty = ~entryValids.asUInt.andR
  val emptyIdx = PriorityEncoder(~entryValids.asUInt)

  val replaceValid = write.info.isBr && !wmatched
  plru.io.replaceValid := replaceValid

  val writeIdx = Mux(hasEmpty, emptyIdx, plru.io.replaceIndex)

  entryValids.zip(entryTags).zip(entryCnters).zipWithIndex.map {
    case (((valid, tag), cnter), idx) =>

      // We need to replace entry when:
      // 1. The write signal is valid (a branch inst is executed)
      // 2. No previous entry matches the branch PC
      // 3. This entry is targeted by id calculated above (empty first, lsu when all full)
      val canReplace = replaceValid && writeIdx === idx.U
      valid := valid | canReplace

      tag := Mux(
        canReplace,
        write.pc.end(conf.PCLen),
        tag,
      )

      cnter.io.enable   := (valid && wmatches(idx)) || canReplace
      cnter.io.op       := write.brTake
      cnter.io.set      := canReplace
      cnter.io.setValue := 0.U
  }

}
