package hpipe.widget

import chisel3._
import chisel3.util._
import harith.uint._
import hpipe.HPipeParameters

object UIntAdd {

  def apply(width: Int)(implicit p: HPipeParameters) =
    if (p.UseArithMacro) new UIntMacroAdd(width)
    else new UIntPrefixAdd(width, PrefixStyle.HanCarlson)

  def apply(width: Int, a: UInt, b: UInt, c: Bool)(implicit
      p: HPipeParameters,
  ): UInt = {
    val mod = Module(UIntAdd(width))
    mod.io.src1  := a
    mod.io.src2  := b
    mod.io.carry := c
    mod.io.output
  }

  def apply(width: Int, a: UInt, b: UInt)(implicit p: HPipeParameters): UInt =
    apply(width, a, b, false.B)
}
