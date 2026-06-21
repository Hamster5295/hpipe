package hpipe

import chisel3._
import chisel3.util._
import hammer._

class PipeMemIO(implicit p: HPipeParameters) extends StageIO {
  val memLoad  = new MemLoadIO
  val memStore = new MemStoreIO

  val fromEx = Input(new Ex2MemIO)
  val toWb   = Output(new Mem2WbIO)

  val feedForward = new DestInfo
}

class PipeMem(implicit val p: HPipeParameters) extends Module {
  val io     = IO(new PipeMemIO)
  val fromEx = io.fromEx

  io.busy := false.B

  // Load
  io.memLoad.req.valid := fromEx.flags.ld
  io.memLoad.req.addr  := fromEx.addr
  val loaded = io.memLoad.data
  val result = MuxLookup(fromEx.funct, 0.U)(Seq(
    LoadOp.Byte.asUInt  -> SignExt(loaded.end(8), 32),
    LoadOp.Half.asUInt  -> SignExt(loaded.end(16), 32),
    LoadOp.Word.asUInt  -> loaded,
    LoadOp.UByte.asUInt -> loaded.end(8),
    LoadOp.UHalf.asUInt -> loaded.end(16),
  ))

  // Store
  io.memStore.req.valid := fromEx.flags.st
  io.memStore.req.addr  := fromEx.addr
  io.memStore.req.data  := fromEx.data
  io.memStore.req.mask  := MuxLookup(fromEx.funct, 0.U)(Seq(
    StoreOp.Byte.asUInt -> "b0001".U,
    StoreOp.Half.asUInt -> "b0011".U,
    StoreOp.Word.asUInt -> "b1111".U,
  ))

  val data = Mux(fromEx.flags.ld, result, fromEx.data)

  val toWb = io.toWb
  toWb      := fromEx
  toWb.data := data

  val toId = io.feedForward
  toId.gpr.valid     := fromEx.flags.writeRd && fromEx.rd.orR
  toId.gpr.bits.addr := fromEx.rd
  toId.gpr.bits.data := data
  toId.gpr.bits.isLd := fromEx.flags.ld

  toId.csr.valid     := fromEx.flags.csr && fromEx.csrAddr.orR
  toId.csr.bits.addr := fromEx.csrAddr
  toId.csr.bits.data := fromEx.csrData
}
