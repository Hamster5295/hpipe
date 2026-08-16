package hpipe

import chisel3._
import chisel3.util._
import hammer._
import scala.collection.mutable

class FullAdd(optSize: Boolean = false) extends Module {
  val io = IO(new Bundle {
    val a  = Input(Bool())
    val b  = Input(Bool())
    val c  = Input(Bool())
    val o  = Output(Bool())
    val co = Output(Bool())
  })

  if (optSize) {
    // 2 XOR2s, 2 AND2s, 1 OR2
    val internal = WireInit(io.a ^ io.b)
    io.o  := internal ^ io.c
    io.co := (internal & io.c) | (io.a & io.b)

  } else {
    // 2 XOR2s, 3 AND2s, 1 OR3
    io.o  := io.a ^ io.b ^ io.c
    io.co := (io.a & io.b) | (io.a & io.c) | (io.b & io.c)
  }
}

class CSA(width: Int) extends Module {
  val io = IO(new Bundle {
    val a  = Input(UInt(width.W))
    val b  = Input(UInt(width.W))
    val c  = Input(UInt(width.W))
    val o  = Output(UInt(width.W))
    val co = Output(UInt(width.W))
  })

  val o  = Wire(Vec(width, Bool()))
  val co = Wire(Vec(width + 1, Bool()))

  co(0) := 0.U

  Seq.range(0, width).map { i =>
    val adder = Module(new FullAdd)
    adder.io.a := io.a(i)
    adder.io.b := io.b(i)
    adder.io.c := io.c(i)
    o(i)       := adder.io.o
    co(i + 1)  := adder.io.co
  }

  io.o  := o.asUInt
  io.co := co.asUInt.tail(1)
}

class IntBoothMul(width: Int) extends AbstractIntMul(width) {

  val cnt = ((width + 1f) / 2).ceil.toInt

  def ext(in: UInt, signed: Bool): UInt =
    if (width % 2 != 0) (signed && in.head(1).asBool) ## in
    else Fill(2, signed && in.head(1).asBool) ## in

  val a = ext(io.a, io.aSigned)
  val b = ext(io.b, io.bSigned) ## 0.U(1.W)

  val booths =
    Seq.range(0, cnt).map { i =>
      val res = Booth4(a, b(i * 2 + 2, i * 2)) << (i * 2)
      if (res.getWidth < width * 2)
        Fill(width * 2 - res.getWidth, res.head(1)) ## res
      else res(width * 2 - 1, 0)
    }

  val queue = mutable.Queue[UInt](booths: _*)
  while (queue.length > 2) {
    val adder = Module(new CSA(width * 2))
    adder.io.a := queue.dequeue()
    adder.io.b := queue.dequeue()
    adder.io.c := queue.dequeue()
    queue.enqueue(adder.io.o)
    queue.enqueue(adder.io.co)
  }

  val adder = Module(new UIntCla(width * 2, 4))
  adder.io.a := queue.dequeue()
  adder.io.b := queue.dequeue()
  adder.io.c := false.B
  io.o       := adder.io.o

  io.busy := false.B
}
