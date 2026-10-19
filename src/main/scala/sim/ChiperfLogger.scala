package hpipe.sim

import chiperf._
import chiperf.event._
import chisel3._
import chisel3.util._
import chisel3.util.experimental._
import hammer._
import hpipe._

class ChiperfLogger(hpipe: HPipe, debugger: SimDebugger)(implicit
    p: HPipeParameters,
) extends ChiperfModule("hpipe.chiperf", "HPipe") {

  val pIf  = Pip("If")
  val pId  = Pip("Id")
  val pSg  = Pip("Sg")
  val pEx  = Pip("Ex")
  val pMem = Pip("Mem")
  val pWb  = Pip("Wb")

  val pc = Val("pc")
  val sp = Val("sp")

  val btb = Val("BTB Cnt")
  val ras = Val("RAS Ptr")

  val brHit  = Evt("Branch Hit")
  val brMiss = Evt("Branch Miss")

  when(clock.asBool) {
    Clk()

    /// Pipeline
    pIf.printWithBubble(
      cf"0x${probe(hpipe.pipeIf.toId.pc)}%8x",
      !probe(hpipe.pipeIf.io.busy),
    )

    def printPipe(pip: Pip, io: PipeIO) = {
      val sig = probe(io)
      pip.printWithBubble(cf"0x${sig.inst}%8x", !sig.valid)
    }

    printPipe(pId, hpipe.pipeId.io.fromIf)
    printPipe(pSg, hpipe.pipeSg.io.fromId)
    printPipe(pEx, hpipe.pipeEx.io.fromSg)
    printPipe(pMem, hpipe.pipeMem.io.fromEx)
    printPipe(pWb, hpipe.pipeWb.io.fromMem)

    /// Values
    pc.print(cf"0x${probe(hpipe.pipeIf.io.toId.pc)}%8x")
    sp.print(cf"0x${probe(debugger.regs.sp)}%8x")

    /// Branch
    btb.print(cf"0x${probe(hpipe.pipeIf.pred.btb.count)}%8x")
    ras.print(cf"${probe(hpipe.pipeIf.pred.ras.ptr)}")

    /// Evts
    when(probe(hpipe.branch.valid)) {
      when(probe(hpipe.redirect))(brMiss.print())
        .otherwise(brHit.print())
    }
  }
}
