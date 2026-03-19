package hpipe

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.scalatest.ChiselSim
import hammer.model.Fixed
import hammer.test.Sim
import hammer.test.Test
import java.util.Random
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class MulSpec extends AnyFreeSpec with Matchers with ChiselSim {
  "Mul should multiply correctly" in Sim(new IntBoothMul(32)) { dut =>
    Test("UInt", dut) {
      dut =>
        dut.io.aSigned.poke(false)
        for (i <- 0 until 1024) {
          val a = Fixed.uint(32)
          val b = Fixed.uint(32)
          dut.io.a.poke(a)
          dut.io.b.poke(b)
          dut.clock.step()
          dut.io.o.expect((a * b) & Fixed.mask(64))
        }
    }
    Test("SInt", dut) {
      dut =>
        dut.io.aSigned.poke(true)
        dut.io.bSigned.poke(true)
        for (i <- 0 until 1024) {
          val a = Fixed.sint(32)
          val b = Fixed.sint(32)
          dut.io.a.poke(a)
          dut.io.b.poke(b)
          dut.clock.step()
          dut.io.o.expect((a * b) & Fixed.mask(64))
        }
    }
  }
}
