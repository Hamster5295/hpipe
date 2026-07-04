package hpipe

import chisel3._
import chisel3.util._
import hammer._

class TargetEntry(implicit p: HPipeParameters) extends Bundle {
  val valid  = Bool()
  val tag    = UInt(p.TargetBuf.TagWidth.W)
  val target = Addr()
}

class TargetBufferIO(implicit p: HPipeParameters) extends Bundle {
  val pc     = Input(Addr())
  val hit    = Output(Bool())
  val target = Output(Addr())

  val writeEnable = Input(Bool())
  val writePc     = Input(Addr())
  val writeData   = Input(Addr())
}

class TargetBuffer(implicit p: HPipeParameters) extends Module {
  val indexWidth = log2Ceil(p.TargetBuf.Size)

  val io         = IO(new TargetBufferIO)
  val query      = io.pc.end(p.TargetBuf.TagWidth)
  val writeQuery = io.writePc.end(p.TargetBuf.TagWidth)

  val entries = RegZero(Vec(p.TargetBuf.Size, new TargetEntry))
  val plru    = Module(new PseudoLruSelector(p.TargetBuf.Size))

  // Read
  val hits = entries.withIndex(indexWidth).map(e =>
    (e.bits.valid && e.bits.tag === query) ## e.index ## e.bits.target,
  )
  val hitEntry = hits.treeReduce((_, l, r) =>
    Mux(l.msb(), l, r),
  )
  io.hit    := hitEntry.msb()
  io.target := hitEntry.tail(1 + indexWidth)
  val hitIndex = hitEntry.get(-1, -indexWidth)

  plru.io.hitValid := hitEntry.msb()
  plru.io.hitIndex := hitIndex

  // Write
  val wen       = io.writeEnable
  val writeHits = entries.map(e => e.valid && e.tag === writeQuery)
  val writeHit  = writeHits.asUInt.orR

  val replaces = entries.zipWithIndex.map { case (e, idx) =>
    val hit     = writeHits(idx)
    val replace = !writeHit && plru.io.replaceIndex === idx.U

    e.valid  := e.valid || (wen && replace)
    e.tag    := Mux(wen && replace, writeQuery, e.tag)
    e.target := MuxIf(
      !wen             -> e.target,
      (hit || replace) -> io.writeData,
    )(e.target)
  }

  plru.io.replaceValid := wen && !writeHit
}
