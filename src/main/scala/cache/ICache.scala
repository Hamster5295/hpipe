package hpipe.cache

import chisel3._
import chisel3.util._
import hpipe._

class ICacheIO(implicit p: HPipeParameters) extends Bundle {
  val coreRead = Flipped(new CoreReadPort)

  val busRead  = new CacheReadPort
  val busWrite = new CacheWritePort
}

class ICache(implicit p: HPipeParameters) extends Module {
  val io = IO(new ICacheIO)

//   val mem = SyncReadMem()
}
