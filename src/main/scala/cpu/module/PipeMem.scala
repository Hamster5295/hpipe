package hpipe

import chisel3._
import chisel3.util._
import hammer._

class PipeMemIO(implicit p: HPipeParameters) extends StageIO {
  val memLoad  = new MemLoadIO
  val memStore = new MemStoreIO

  val fromEx = Input(new Ex2MemIO)
  val toWb   = Output(new Mem2WbIO)

  val feedForward = new FeedForward
}

class PipeMem(implicit val p: HPipeParameters) extends Module {
  val io     = IO(new PipeMemIO)
  val fromEx = io.fromEx

  io.busy := false.B

  // Load
  io.memLoad.req  := fromEx.uop.isLd
  io.memLoad.addr := fromEx.addr
  val loaded = io.memLoad.data
  val result = MuxLookup(fromEx.funct, 0.U)(Seq(
    LoadOp.Byte.asUInt  -> SignExt(loaded.end(8), 32),
    LoadOp.Half.asUInt  -> SignExt(loaded.end(16), 32),
    LoadOp.Word.asUInt  -> loaded,
    LoadOp.UByte.asUInt -> loaded.end(8),
    LoadOp.UHalf.asUInt -> loaded.end(16),
  ))

  // Store
  io.memStore.req  := fromEx.uop.isSt
  io.memStore.addr := fromEx.addr
  io.memStore.data := fromEx.data
  io.memStore.mask := MuxLookup(fromEx.funct, 0.U)(Seq(
    StoreOp.Byte.asUInt -> "b0001".U,
    StoreOp.Half.asUInt -> "b0011".U,
    StoreOp.Word.asUInt -> "b1111".U,
  ))

  val data = Mux(fromEx.uop.isLd, result, fromEx.data)

  val toWb = io.toWb
  toWb.valid    := fromEx.valid
  toWb.pc       := fromEx.pc
  toWb.rd       := fromEx.rd
  toWb.writeRd  := fromEx.uop.writeRd
  toWb.data     := data
  toWb.isEbreak := fromEx.uop.isEBreak
  toWb.isCsr    := fromEx.uop.isCsr
  toWb.csr      := fromEx.csr
  toWb.csrOp    := fromEx.funct

  val toId = io.feedForward
  toId.rd      := fromEx.rd
  toId.isWrite := fromEx.uop.writeRd
  toId.isLd    := fromEx.uop.isLd
  toId.data    := data
}
