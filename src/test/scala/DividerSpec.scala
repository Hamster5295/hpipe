package hpipe

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.scalatest.ChiselSim
import hammer.model.Fixed
import hammer.test._
import java.util.Random
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class DividerSpec extends AnyFreeSpec with Matchers with ChiselSim {

  def sim(width: Int) = Sim(new NonRestoringDiv(width)) { dut =>
    Test(s"UInt$width", dut) { dut =>
      dut.io.signed.poke(false)
      for (i <- 0 until 1024) {
        val a = Fixed.uint(width)
        val b = Fixed.uint(width).max(BigInt(1))
        // val a   = BigInt(10)
        // val b   = BigInt(3)
        val quo = a / b
        val rem = a % b

        dut.io.valid.poke(true)
        dut.io.dividend.poke(a)
        dut.io.divisor.poke(b)
        dut.clock.step()
        dut.io.valid.poke(false)

        while (dut.io.busy.peekBoolean()) {
          dut.clock.step()
        }

        dut.io.quotient.expect(quo)
        dut.io.remainder.expect(rem)
      }
    }
    Test(s"SInt$width", dut) { dut =>
      dut.io.signed.poke(true)
      for (i <- 0 until 1024) {
        val a     = Fixed.sint(width)
        val btemp = Fixed.sint(width)

        val b = if (btemp == 0) BigInt(1) else btemp

        val quo = a / b
        val rem = a % b

        dut.io.valid.poke(true)
        dut.io.dividend.poke(a)
        dut.io.divisor.poke(b)
        dut.clock.step()
        dut.io.valid.poke(false)

        while (dut.io.busy.peekBoolean()) {
          dut.clock.step()
        }

        Expect(
          dut.io.quotient,
          quo & Fixed.mask(width),
        )(ob => ob & Fixed.mask(width))

        Expect(
          dut.io.remainder,
          rem & Fixed.mask(width),
        )(ob => ob & Fixed.mask(width))
      }
    }
  }

  "Divider should divide correctly" in sim(32)

}
