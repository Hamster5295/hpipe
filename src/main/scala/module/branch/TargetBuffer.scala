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
  val ways            = p.TargetBuf.Ways
  val groups          = p.TargetBuf.Size / ways
  val groupIndexWidth = log2Ceil(groups)
  val tagWidth        = p.TargetBuf.TagWidth
  val skipPcBits      = if (p.ExtC) 1 else 2

  val io = IO(new TargetBufferIO)

  val pc      = io.pc.head(p.AddrWidth - skipPcBits)
  val writePc = io.writePc.head(p.AddrWidth - skipPcBits)

  val groupIdx      = pc.end(groupIndexWidth)
  val tag           = pc.span(groupIndexWidth, tagWidth)
  val writeGroupIdx = writePc.end(groupIndexWidth)
  val writeTag      = writePc.span(groupIndexWidth, tagWidth)

  val entries = RegZero(Vec(groups, Vec(ways, new TargetEntry)))
  val plrus   = Seq.fill(groups)(Module(new PseudoLruSelector(ways)))

  // Read
  val readGroup = entries(groupIdx)
  val hits      = VecInit(readGroup.map(g => g.valid && g.tag === tag))
  val hit       = hits.asUInt.orR
  val hitIndex  = OHToUInt(hits)

  io.hit    := hit
  io.target := readGroup(hitIndex).target

  plrus.zipWithIndex.map { case (plru, idx) =>
    val valid = groupIdx === idx.U
    plru.io.hitValid := valid && hit
    plru.io.hitIndex := hitIndex
  }

  // Write
  val writeGroup = entries(writeGroupIdx)
  val writeHits  = VecInit(writeGroup.map(g => g.valid && g.tag === writeTag))
  val writeHit   = writeHits.asUInt.orR
  val writeHitIndex = OHToUInt(writeHits)

  val replace = writeGroup.zipWithIndex.map { case (entry, idx) =>
    val change  = writeHit && writeHitIndex === idx.U
    val replace = !writeHit &&
      plrus.map(_.io.replaceIndex).asVec(writeGroupIdx) === idx.U

    entry.valid  := io.writeEnable && replace || entry.valid
    entry.tag    := Mux(io.writeEnable && replace, writeTag, entry.tag)
    entry.target :=
      Mux(io.writeEnable && (replace || change), io.writeData, entry.target)

    io.writeEnable && replace
  }.asUInt.orR

  plrus.zipWithIndex.map { case (plru, idx) =>
    val valid = writeGroupIdx === idx.U
    plru.io.replaceValid := replace
  }
}
