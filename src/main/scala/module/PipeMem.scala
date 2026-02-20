package hpipe

import chisel3._
import chisel3.util._
import hammer._

class PipeMemIO(implicit p: Parameters) extends Bundle {
  val memLoad  = new MemLoadIO
  val memStore = new MemStoreIO
  val fromEx   = Flipped(new Ex2MemIO)
  val toWb     = new Mem2WbIO

  val toId = new FeedForward
}

class PipeMem(implicit p: Parameters) extends Module {
  val io     = IO(new PipeMemIO)
  val fromEx = io.fromEx

  // Load
  io.memLoad.req  := fromEx.uop.isLd
  io.memLoad.addr := fromEx.addr
  val loaded = io.memLoad.data
  val result = MuxLookup(fromEx.funct, 0.U)(Seq(
    LoadOp.Byte.asUInt  -> SignExt(loaded.end(8), 32),
    LoadOp.Half.asUInt  -> SignExt(loaded.end(16), 32),
    LoadOp.Word.asUInt  -> loaded,
    LoadOp.UByte.asUInt -> loaded.end(8),
    LoadOp.UHalf.asUInt -> loaded.end(16)
  ))

  // Store
  io.memStore.req  := fromEx.uop.isSt
  io.memStore.addr := fromEx.addr
  io.memStore.data := fromEx.alu
  io.memStore.mask := MuxLookup(fromEx.funct, 0.U)(Seq(
    StoreOp.Byte.asUInt -> "b0001".U,
    StoreOp.Half.asUInt -> "b0011".U,
    StoreOp.Word.asUInt -> "b1111".U
  ))

  val data = Mux(fromEx.uop.isLd, result, fromEx.alu)

  val toWb = io.toWb
  toWb.valid   := fromEx.valid
  toWb.pc      := fromEx.pc
  toWb.rd      := fromEx.rd
  toWb.writeRd := fromEx.uop.writeRd
  toWb.data    := data
  toWb.ebreak  := fromEx.uop.isEBreak

  val toId = io.toId
  toId.rd      := fromEx.rd
  toId.isWrite := fromEx.uop.writeRd
  toId.isLd    := fromEx.uop.isLd
  toId.data    := data
}
