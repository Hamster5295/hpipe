package hpipe

import chisel3._
import chisel3.util._
import hammer._
import hammer.model.Fixed

class BranchReadPort(implicit p: Parameters) extends Bundle {
  val pc     = Input(Addr())
  val brAddr = Input(Addr())

  val isJalr = Input(Bool())
  val isJal  = Input(Bool())
  val isBr   = Input(Bool())

  val nextpc = Output(Addr())
  val take   = Output(Bool())
}

class BranchWritePort(implicit p: Parameters) extends Bundle {
  val valid = Input(Bool())
  val pc    = Input(Addr())
  val take  = Input(Bool())
}

class BTBIO(implicit p: Parameters) extends Bundle {
  val read  = new BranchReadPort
  val write = new BranchWritePort
}

class BTB(implicit p: Parameters) extends Module {
  val io = IO(new BTBIO)

  // Entries
  // Is this entry valid?
  val entryValids = RegZero(Vec(p.BranchEntryCount, Bool()))
  // The PC tag of the entry
  val entryTags = RegZero(Vec(p.BranchEntryCount, UInt(p.BranchEntryPCLen.W)))
  // The counter of the prediction
  val entryCnters = Seq.fill(p.BranchEntryCount)(
    Module(SaturateCounter(
      p.BranchEntryCnterWidth,
      Fixed.mask(p.BranchEntryCnterWidth - 1)
    ))
  )
  val entryCnterValues = VecInit(entryCnters.map(i => i.io.value))
  // How long since this entry last used?
  val entryUseds = Seq.fill(p.BranchEntryCount)(
    Module(SaturateCounter(
      p.BranchEntryLRUWidth,
      0
    ))
  )
  val entryUsedValues = VecInit(entryUseds.map(i => i.io.value))

  // Write
  val write    = io.write
  val wmatches =
    VecInit(entryTags.map(i => i === write.pc.end(p.BranchEntryPCLen)))
  val wmatched = wmatches.asUInt.orR

  // LRU Calculations for write pointer
  val widx =
    entryUsedValues.withIndex(p.BranchEntryPtrWidth).reduceTree { (l, r) =>
      val width = l.data.getWidth + 1
      val cmp = UIntCLA(width)(l.data.pad(width), ~r.data.pad(width), 1.B).msb(1)
      Mux(cmp, r, l)
    }.idx

  entryValids.zip(entryTags).zip(entryCnters).zipWithIndex.map {
    case (((valid, tag), cnter), idx) =>

      // We need to write but no previous matched, also this entry is the LRU one
      // So this entry will be overriden
      val canOverride = write.valid && !wmatched && widx === idx.U
      valid := valid | canOverride

      tag := Mux(
        canOverride,
        write.pc.end(p.BranchEntryPCLen),
        tag
      )

      cnter.io.enable   := (valid && wmatches(idx)) || canOverride
      cnter.io.op       := write.take
      cnter.io.set      := false.B
      cnter.io.setValue := 0.U
  }

  // Read
  val read      = io.read
  val defaultPc = read.pc +% 4.U

  val rmatches =
    VecInit(entryTags.map(i => i === read.pc.end(p.BranchEntryPCLen)))
  val rmatched = rmatches.asUInt.orR

  val ridx = PriorityEncoder(rmatches.asUInt)
  read.nextpc := Mux(
    (read.isJal || read.isJalr) ||
      (read.isBr && rmatched && entryCnterValues(ridx).msb()),
    read.brAddr,
    defaultPc
  )

  read.take :=
    (read.isJal || read.isJalr) ||
      (read.isBr && rmatched && entryCnterValues(ridx).msb())

  entryUseds.zipWithIndex.map {
    case (used, idx) =>
      used.io.enable   := true.B
      used.io.op       := true.B
      used.io.set      := rmatches(idx)
      used.io.setValue := 0.U
  }
}
