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

  val pointer     = RegInit(0.U(p.BranchEntryPtrWidth.W))
  val entryValids = RegZero(Vec(p.BranchEntryCount, Bool()))
  val entryTags   = RegZero(Vec(p.BranchEntryCount, UInt(p.BranchEntryPCLen.W)))
  val entryCnters = Seq.fill(p.BranchEntryCount)(Module(SaturateCounter(
    p.BranchEntryCnterWidth,
    Fixed.mask(p.BranchEntryCnterWidth - 1)
  )))
  val entryCnterValues = VecInit(entryCnters.map(i => i.io.next))

  // Write
  val write    = io.write
  val wmatches =
    VecInit(entryTags.map(i => i === write.pc.end(p.BranchEntryPCLen)))
  val wmatched = wmatches.asUInt.orR

  pointer := Mux(wmatched || !write.valid, pointer, pointer +% 1.U)

  entryValids.zip(entryTags).zip(entryCnters).zipWithIndex.map {
    case (((valid, tag), cnter), idx) =>

      val canWrite = !wmatched && pointer === idx.U && write.valid
      valid := valid | canWrite

      tag := Mux(
        canWrite,
        write.pc.end(p.BranchEntryPCLen),
        tag
      )

      cnter.io.enable := (valid && wmatches(idx)) || canWrite
      cnter.io.op     := write.take
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

//   dontTouch(read)
//   dontTouch(write)
}
