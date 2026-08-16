package hpipe

import chisel3._
import chisel3.util._

class UIntMacroAdd(width: Int) extends AbstractUIntAdd(width) {
  io.o := io.a + io.b + io.c
}
