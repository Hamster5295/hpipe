package hpipe

import chisel3._
import chisel3.util._
import chisel3.util.experimental._
import hammer._

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
      def printOX(name: String, result: PipeIO, pass: PipeIO) = {
        val res = RegZero(new PipeIO)
        res := result
        when(res.valid && pass.valid) {
          output.printf(
            cf"[pip] \"$name\", O, 0x${res.inst}%x\n",
          )
        }

        when(res.valid && !pass.valid) {
          output.printf(
            cf"[pip] \"$name\", X, 0x${res.inst}%x\n",
          )
        }
      }

      // If
      when(get(hpipe.pipeIf.fetchValid)) {
        output.printf(cf"[pip] \"IF\", I, 0x${get(hpipe.pipeIf.inst)}%x\n")
      }

      printOX("IF", get(hpipe.pipeIf.io.toId), get(hpipe.pipeId.io.fromIf))

      // Id
      when(get(hpipe.pipeId.io.fromIf.valid)) {
        output.printf(
          cf"[pip] \"ID\", I, 0x${get(hpipe.pipeId.io.fromIf.inst)}%x\n",
        )
      }

      printOX("ID", get(hpipe.pipeId.io.toSg), get(hpipe.pipeSg.io.fromId))

      // Sg
      when(get(hpipe.pipeSg.io.fromId.valid)) {
        output.printf(
          cf"[pip] \"SG\", I, 0x${get(hpipe.pipeSg.io.fromId.inst)}%x\n",
        )
      }

      printOX("SG", get(hpipe.pipeSg.io.toEx), get(hpipe.pipeEx.io.fromSg))

      // Ex
      when(get(hpipe.pipeEx.io.fromSg.valid)) {
        output.printf(
          cf"[pip] \"EX\", I, 0x${get(hpipe.pipeEx.io.fromSg.inst)}%x\n",
        )
      }

      printOX("EX", get(hpipe.pipeEx.io.toMem), get(hpipe.pipeMem.io.fromEx))

      // Mem
      when(get(hpipe.pipeMem.io.fromEx.valid)) {
        output.printf(
          cf"[pip] \"MEM\", I, 0x${get(hpipe.pipeMem.io.fromEx.inst)}%x\n",
        )
      }

      printOX("MEM", get(hpipe.pipeMem.io.toWb), get(hpipe.pipeWb.io.fromMem))

      // Wb
      when(get(hpipe.pipeWb.io.fromMem.valid)) {
        output.printf(
          cf"[pip] \"WB\", I, 0x${get(hpipe.pipeWb.io.fromMem.inst)}%x\n",
        )
      }

      val delayWb = RegNext(get(hpipe.pipeWb.io.retire))
      when(delayWb.valid) {
        output.printf(
          cf"[pip] \"WB\", O, 0x${delayWb.inst}%x\n",
        )
      }
    }

    output.printf(
      "[val] \"pc\", 0x%8x\n",
      get(hpipe.pipeIf.pc),
    )
  }
}
