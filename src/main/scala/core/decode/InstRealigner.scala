package hpipe

import chisel3._
import chisel3.util._
import hammer._

class InstRealignerInput(implicit p: HPipeParameters) extends Bundle {
  val inst = Inst()
  val req  = new PcReqInfo
}

class InstRealignerOutput(implicit p: HPipeParameters) extends Bundle {
  val inst = Inst()
  val isC  = Bool()
  val pc   = Addr()
  val pred = new BranchPredictInfo
}

class InstRealignerIO(implicit p: HPipeParameters) extends Bundle {
  val input = Flipped(Decoupled(new InstRealignerInput))
  val flush = Input(Bool())

  val output = Decoupled(new InstRealignerOutput)
}

class InstRealigner(implicit p: HPipeParameters) extends Module {
  val io = IO(new InstRealignerIO)

  val input  = io.input
  val output = io.output
  val req    = input.bits.req

  val sIdle :: sPartial :: sSplit :: sSplitDone :: Nil = Enum(4)

  val state       = RegInit(sIdle)
  val isIdle      = state === sIdle
  val isPartial   = state === sPartial
  val isSplit     = state === sSplit
  val isSplitDone = state === sSplitDone

  val buffer = RegZero(UInt((p.XLEN / 2).W))
  val pc     = RegZero(Addr())
  val pred   = RegZero(new BranchPredictInfo)

  val inst       = input.bits.inst
  val pcInMiddle = input.bits.req.pcInMiddle
  val endI       = inst.end(2).andR
  val midI       = inst.span(16, 2).andR

//   assert(
//     !half || half && isIdle,
//     "Half signal should only be valid when state === sIdle, as it's expected to appear only when redirection / trap",
//   )

  input.ready      := output.ready
  output.valid     := input.valid
  output.bits.inst := input.bits.inst
  output.bits.isC  := false.B
  output.bits.pc   := input.bits.req.pc
  output.bits.pred := input.bits.req.pred

  switch(state) {
    is(sIdle) {
      val isI  = endI
      val isIc = !endI && midI
      val isCc = !endI && !midI

      val flushUpper = req.pred.take && !req.predPcInMiddle

      input.ready             := output.ready
      output.valid            := input.valid
      output.bits.inst        := inst
      output.bits.isC         := !endI
      output.bits.pc          := req.pc
      output.bits.pred.stepPc := req.pc + Mux(output.bits.isC, 2.U, 4.U)

      when(input.fire) {
        buffer := Mux(isIc || isCc, inst.head(16), 0.U)
        pc     := req.pc
        pred   := req.pred

        state := Mux(
          flushUpper,
          sIdle,
          Mux1H(Seq(
            isI  -> sIdle,
            isIc -> sSplit,
            isCc -> sPartial,
          )),
        )
      }
    }

    is(sPartial) {
      input.ready             := false.B
      output.valid            := true.B
      output.bits.inst        := buffer.end(16)
      output.bits.isC         := true.B
      output.bits.pc          := pc +% 2.U
      output.bits.pred        := pred
      output.bits.pred.stepPc := pc +% 4.U // pc + 2 + 2 (must be 2bit)

      when(output.fire)(state := sIdle)
    }

    is(sSplit) {
      val isIi = midI
      val isCi = !midI

      input.ready             := output.ready
      output.valid            := input.valid
      output.bits.inst        := inst.end(16) ## buffer
      output.bits.isC         := false.B
      output.bits.pc          := pc +% 2.U
      output.bits.pred        := pred
      output.bits.pred.stepPc := pc +% 6.U // pc + 2 + 4 (must be 4bit)

      when(input.fire && output.fire)
      {
        buffer := inst.head(16)
        pc     := input.bits.req.pc
        pred   := input.bits.req.pred

        state := Mux1H(Seq(
          isIi -> sSplit,
          isCi -> sPartial,
        ))
      }
    }
  }

  // For this case, the input inst is in layout of:
  // - xI
  // - xC
  // Where we don't care about x
  when(pcInMiddle) {
    input.ready  := output.ready
    output.valid := false.B

    when(input.fire) {
      buffer := inst.head(16)
      pc     := input.bits.req.pc
      pred   := input.bits.req.pred

      state := Mux(midI, sSplit, sPartial)
    }
  }

  when(io.flush) {
    state  := sIdle
    buffer := 0.U
    pc     := 0.U
  }
}
