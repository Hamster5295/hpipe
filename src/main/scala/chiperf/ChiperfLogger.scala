package hpipe.chiperf

import chisel3._
import chisel3.util._
import chisel3.util.experimental._
import hammer._
import hpipe._

class ChiperfLogger(hpipe: HPipe)(implicit p: HPipeParameters) extends Module {
  val output = SimLog.file("hpipe.chiperf")

  def get[A <: Data](source: A) = BoringUtils.tapAndRead(source)

  val resets = Reg(Vec(2, Bool()))
  resets(0) := reset.asBool
  resets(1) := resets(0)

  when(!resets(1) && resets(0)) {
    output.printf("[rst]\n\n");
    output.printf("chiperf 1.0\n@meta design=\"hpipe\"\n\n");
  }

  when(clock.asBool) {
    output.printf("[clk] p\n")

    // Pipeline
    {
      def printPip(name: String, valid: Bool, inst: UInt) =
        when(valid)(output.printf(cf"[pip] $name, 0x${inst}%8x\n"))
          .otherwise(output.printf(cf"[pip] $name, bubble\n"))

      def printPipe(name: String, io: PipeIO) =
        printPip(name, io.valid, io.inst)

      printPip("If", get(hpipe.pipeIf.fetchValid), get(hpipe.pipeIf.inst))
      printPipe("Id", get(hpipe.pipeId.io.fromIf))
      printPipe("Sg", get(hpipe.pipeSg.io.fromId))
      printPipe("Ex", get(hpipe.pipeEx.io.fromSg))
      printPipe("Mem", get(hpipe.pipeMem.io.fromEx))
      printPipe("Wb", get(hpipe.pipeWb.io.fromMem))
    }

    output.printf(
      "[val] \"pc\", 0x%8x\n",
      get(hpipe.pipeIf.pc),
    )

    when(get(hpipe.branch.valid)) {
      when(get(hpipe.branch.redirect))(output.printf("[evt] \"Branch Miss\"\n"))
        .otherwise(output.printf("[evt] \"Branch Hit\"\n"))
    }
  }
}
