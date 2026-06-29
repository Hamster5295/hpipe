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

  // How long since this entry last used?
  val entryUseds = Seq.fill(conf.Count)(
    Module(SaturateCounter(conf.LruWidth, 0)),
  )
  val entryUsedValues = VecInit(entryUseds.map(i => i.io.value))

  // Write
  val write    = io.write
  val wmatches =
    VecInit(entryTags.map(i => i === write.pc.end(conf.PCLen)))
  val wmatched = wmatches.asUInt.orR

  // We write empty entries first
  val hasEmpty = ~entryValids.asUInt.andR
  val emptyIdx = PriorityEncoder(~entryValids.asUInt)

  // LRU Calculations for write pointer
  val lruIdx =
    entryUsedValues.withIndex(conf.PtrWidth).treeReduce {
      (_, l, r) =>
        val width = l.bits.getWidth + 1
        val cmp   =
          UIntCLA(width)(l.bits.pad(width), ~r.bits.pad(width), 1.B).msb(1)
        Mux(cmp, r, l)
    }.index

  val writeIdx = Mux(hasEmpty, emptyIdx, lruIdx)

  entryValids.zip(entryTags).zip(entryCnters).zipWithIndex.map {
    case (((valid, tag), cnter), idx) =>

      // We need to override entry when:
      // 1. The write signal is valid (a branch inst is executed)
      // 2. No previous entry matches the branch PC
      // 3. This entry is targeted by id calculated above (empty first, lsu when all full)
      val canOverride = write.info.isBr && !wmatched && writeIdx === idx.U
      valid := valid | canOverride

      tag := Mux(
        canOverride,
        write.pc.end(conf.PCLen),
        tag,
      )

      cnter.io.enable   := (valid && wmatches(idx)) || canOverride
      cnter.io.op       := write.brTake
      cnter.io.set      := canOverride
      cnter.io.setValue := 0.U
  }

  // Read
  val read     = io.read
  val rmatches =
    VecInit(entryTags.map(i => i === read.pc.end(conf.PCLen)))
  val rmatched = rmatches.asUInt.orR

  val ridx = PriorityEncoder(rmatches.asUInt)
  read.target := Mux(
    read.info.isBr && rmatched && entryCnterValues(ridx).msb(),
    read.brAddr,
    io.read.defaultTarget,
  )
  read.brTake := read.info.isBr && rmatched && entryCnterValues(ridx).msb()

  entryUseds.zipWithIndex.map {
    case (used, idx) =>
      used.io.enable   := true.B
      used.io.op       := true.B
      used.io.set      := rmatches(idx)
      used.io.setValue := 0.U
  }

}
