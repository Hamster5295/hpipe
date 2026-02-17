package hpipe

import chisel3._
import chisel3.util._
import hammer._

class PipeMemIO(implicit val p: Parameters) extends Bundle {
  val memLoad  = new MemLoadIO
  val memStore = new MemStoreIO
  val fromEx   = Flipped(new Ex2MemIO)
  val toWb     = new Mem2WbIO

  val toId = new FeedForward
}

class PipeMem(implicit val p: Parameters) extends Module {
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
    LoadOp.UByte.asUInt -> (loaded & "b0001".U),
    LoadOp.UHalf.asUInt -> (loaded & "b0011".U)
  ))

  // Store
  io.memStore.req  := fromEx.uop.isSt
  io.memStore.addr := fromEx.addr
  io.memStore.data := fromEx.alu
  io.memStore.mask := MuxLookup(fromEx.funct, 0.U)(Seq(
    StoreOp.Byte.asUInt -> "b1111".U,
    StoreOp.Half.asUInt -> "b0011".U,
    StoreOp.Byte.asUInt -> "b0001".U
  ))

  val toWb = io.toWb
  toWb.pc      := fromEx.pc
  toWb.rd      := fromEx.rd
  toWb.writeRd := fromEx.uop.writeRd
  toWb.data    := Mux(fromEx.uop.isLd, result, fromEx.alu)

  val toId = io.toId
  toId.writeRd := fromEx.uop.writeRd
  toId.rd      := fromEx.rd
  toId.data    := fromEx.alu
}
