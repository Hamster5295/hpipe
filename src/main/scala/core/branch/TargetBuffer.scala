package hpipe.branch

import chisel3._
import chisel3.util._
import hammer._
import hpipe._

class TargetEntry(implicit p: HPipeParameters) extends Bundle {
  val pa     = p.Branch.TargetBuf
  val valid  = Bool()
  val tag    = UInt(pa.TagWidth.W)
  val target = Addr()

  val meta = new BranchPredictMeta
}

class TargetBufferIO(implicit p: HPipeParameters) extends Bundle {
  val pc     = Input(Addr())
  val hit    = Output(Bool())
  val target = Output(Addr())
  val meta   = Output(new BranchPredictMeta)

  val writeEnable = Input(Bool())
  val writePc     = Input(Addr())
  val writeTarget = Input(Addr())
  val writeFlags  = Input(new BranchFlags)
}

class TargetBuffer(implicit p: HPipeParameters) extends Module {
  val pa              = p.Branch.TargetBuf
  val ways            = pa.Ways
  val groups          = pa.Depth / ways
  val groupIndexWidth = log2Ceil(groups)
  val tagWidth        = pa.TagWidth

  def getTag(pc: UInt) = pc.span(groupIndexWidth, tagWidth)

  val io = IO(new TargetBufferIO)

  val pc      = io.pc.head(p.PcUsedWidth)
  val writePc = io.writePc.head(p.PcUsedWidth)

  val groupIdx      = pc.end(groupIndexWidth)
  val tag           = getTag(pc)
  val writeGroupIdx = writePc.end(groupIndexWidth)
  val writeTag      = getTag(writePc)

  dontTouch(tag)
  dontTouch(writeTag)

  val entries = RegZero(Vec(groups, Vec(ways, new TargetEntry)))
  val plrus   = Seq.fill(groups)(Module(new PseudoLruSelector(ways)))

  // Read
  val readGroup = entries(groupIdx)
  val hits      = VecInit(readGroup.map(g => g.valid && g.tag === tag))
  val hit       = hits.asUInt.orR
  val hitIndex  = OHToUInt(hits)

  io.hit    := hit
  io.target := readGroup(hitIndex).target
  io.meta   := readGroup(hitIndex).meta

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
      Mux(io.writeEnable && (replace || change), io.writeTarget, entry.target)
    entry.meta.flags :=
      Mux(io.writeEnable && replace, io.writeFlags, entry.meta.flags)
    entry.meta.pcInMiddle :=
      Mux(io.writeEnable && replace, io.writePc(1), entry.meta.pcInMiddle)

    io.writeEnable && replace
  }.asUInt.orR

  plrus.zipWithIndex.map { case (plru, idx) =>
    val valid = writeGroupIdx === idx.U
    plru.io.replaceValid := replace
  }

  val count = entries.flatMap(i => i).map(_.valid.asUInt).reduce(_ +& _)
}
