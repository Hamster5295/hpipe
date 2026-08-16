package hpipe

import chisel3._
import chisel3.util._
import hammer._
import os.group
import scala.reflect.api.Exprs

class ClaAdder extends Module {
  val io = IO(new Bundle {
    val a = Input(Bool())
    val b = Input(Bool())
    val c = Input(Bool())
    val o = Output(Bool())
    val p = Output(Bool())
    val g = Output(Bool())
  })

  io.p := io.a ^ io.b
  io.g := io.a & io.b
  io.o := io.p ^ io.c
}

class UIntClu(width: Int) extends Module {
  val io = IO(new Bundle {
    val p  = Input(UInt(width.W))
    val g  = Input(UInt(width.W))
    val ci = Input(Bool())
    val co = Output(UInt((width + 1).W))
    val po = Output(Bool())
    val go = Output(Bool())
  })

  val gs = io.g ## io.ci
  val rs = Wire(Vec(width + 1, Bool()))

  for (i <- 0 until width + 1) {
    val temp = Wire(Vec(i + 1, Bool()))
    for (j <- 0 until i + 1) {
      if (i == j) temp(j) := gs(j)
      else temp(j)        := gs(j) & io.p(i - 1, j).andR
    }
    rs(i) := temp.asUInt.orR
  }

  val gos = Wire(Vec(width, Bool()))
  for (i <- 0 until width)
    gos(i) :=
      (if (i == width - 1) io.g(i)
       else io.g(i) & io.p.head(width - 1 - i).andR)

  io.co := rs.asUInt
  io.po := io.p.andR
  io.go := gos.asUInt.orR
}

class UIntCla(width: Int, groupBy: Int) extends AbstractUIntAdd(width) {

  val po = IO(Output(Bool()))
  val go = IO(Output(Bool()))

  if (width <= groupBy) {
    // Create the minimal CLA
    val clu  = Module(new UIntClu(width))
    val adds = Seq.range(0, width).map { i =>
      val add = Module(new ClaAdder())
      add.io.a := io.a(i)
      add.io.b := io.b(i)
      add.io.c := clu.io.co(i)
      add
    }
    clu.io.p := VecInit(adds.map(i => i.io.p)).asUInt
    clu.io.g := VecInit(adds.map(i => i.io.g)).asUInt

    clu.io.ci := io.c
    io.o := clu.io.co.msb() ## VecInit(adds.map(i => i.io.o)).asUInt.end(width)
    // io.po     := clu.io.po
    // io.go     := clu.io.go
    po := clu.io.po
    go := clu.io.go
  } else {
    // Recursively generate smaller CLAs
    var minGroup = groupBy
    while (minGroup * groupBy < width) minGroup *= groupBy
    val cnt = math.ceil(width / minGroup.toFloat).toInt

    val clu = Module(new UIntClu(cnt))

    var wid  = width
    var idx  = 0
    var adds = Seq[UIntCla]()
    while (wid >= minGroup) {
      wid -= minGroup

      val add = Module(new UIntCla(minGroup, groupBy))
      add.io.a := io.a.block(idx, minGroup)
      add.io.b := io.b.block(idx, minGroup)
      add.io.c := clu.io.co(idx)
      adds :+= add

      idx += 1
    }

    if (wid > 0) {
      val add = Module(new UIntCla(wid, groupBy))
      add.io.a := io.a.head(wid)
      add.io.b := io.b.head(wid)
      add.io.c := clu.io.co.msb(1)
      adds :+= add
    }

    io.o := clu.io.co.msb() ## VecInit(adds.map(i =>
      i.io.o.tail(1),
    )).asUInt.end(width)
    clu.io.p := VecInit(adds.map(i => i.po)).asUInt
    clu.io.g := VecInit(adds.map(i => i.go)).asUInt

    clu.io.ci := io.c
    po        := clu.io.po
    go        := clu.io.go
  }
}

object UIntCla {
  def apply(
      width:   Int,
      groupBy: Int = 4,
  )(src1: UInt, src2: UInt, carry: Bool): UInt = {
    val adder = Module(new UIntCla(width, groupBy))
    adder.io.a := src1
    adder.io.b := src2
    adder.io.c := carry
    adder.io.o
  }
}
