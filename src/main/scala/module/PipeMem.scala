package hpipe

import chisel3._
import chisel3.util._
import hammer._

class PipeMemIO(implicit p: HPipeParameters) extends StageIO {
  val memLoad  = new MemLoadPort
  val memStore = new MemStorePort

  val fromEx = Input(new Ex2MemIO)
  val toWb   = Output(new Mem2WbIO)

  val feedForward = new DestInfo
}

class PipeMem(implicit val p: HPipeParameters) extends Module {
  val io     = IO(new PipeMemIO)
  val fromEx = io.fromEx

  // Load
  val loadBusy = RegZero(Bool())

  io.memLoad.addr.valid := fromEx.flags.load && !loadBusy
  io.memLoad.addr.bits  := fromEx.addr

  io.memLoad.data.ready := fromEx.flags.load
  val loaded = io.memLoad.data.bits
  val result = MuxLookup(fromEx.funct, 0.U)(Seq(
    LoadOp.Byte.asUInt  -> SignExt(loaded.end(8), 32),
    LoadOp.Half.asUInt  -> SignExt(loaded.end(16), 32),
    LoadOp.Word.asUInt  -> loaded,
    LoadOp.UByte.asUInt -> loaded.end(8),
    LoadOp.UHalf.asUInt -> loaded.end(16),
  ))

  // See PipeIf.fetchBusy for why it's like this
  loadBusy := Mux(
    io.memLoad.addr.fire ^ io.memLoad.data.fire,
    io.memLoad.addr.fire,
    loadBusy,
  )

  // Store
  io.memStore.req.valid     := fromEx.flags.store
  io.memStore.req.bits.addr := fromEx.addr
  io.memStore.req.bits.data := fromEx.data
  io.memStore.req.bits.mask := MuxLookup(fromEx.funct, 0.U)(Seq(
    StoreOp.Byte.asUInt -> "b0001".U,
    StoreOp.Half.asUInt -> "b0011".U,
    StoreOp.Word.asUInt -> "b1111".U,
  ))

  val data = Mux(fromEx.flags.load, result, fromEx.data)

  val toWb = io.toWb
  toWb      := fromEx
  toWb.data := data

  val toId = io.feedForward
  toId.gpr.valid     := fromEx.flags.writeRd && fromEx.rd.orR
  toId.gpr.bits.addr := fromEx.rd
  toId.gpr.bits.data := data
  toId.gpr.bits.isLd := fromEx.flags.load

  toId.csr.valid     := fromEx.flags.csr && fromEx.csrAddr.orR
  toId.csr.bits.addr := fromEx.csrAddr
  toId.csr.bits.data := fromEx.csrData

  io.busy :=
    (fromEx.flags.load && !io.memLoad.data.fire) ||
      (fromEx.flags.store && !io.memStore.req.fire)
}
