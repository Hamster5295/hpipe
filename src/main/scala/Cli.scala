package hpipe

import chisel3._
import chisel3.util._
import com.gu.spy._
import hammer._
import org.rogach.scallop._
import os.Path
import toml.derivation.auto._

class CliConf(args: Seq[String]) extends ScallopConf(args) {
  val config =
    opt[String](descr = "Provides a toml config for HPipe to generate from")
  val output =
    opt[String]("target-dir", short = 'o', descr = "The output dir of HPipe.sv")
  val firOpts = props[String]('F', descr = "The firtool options to be passed")
  verify()
}

object Cli extends App {
  println(
    """    __  ______  _          
   / / / / __ \(_)___  ___ 
  / /_/ / /_/ / / __ \/ _ \
 / __  / ____/ / /_/ /  __/
/_/ /_/_/   /_/ .___/\___/ 
             /_/           
""",
  )
  println("A 6-stage Pipelined CPU")
  val cli = new CliConf(args.toSeq)

  val conf = if (cli.config.isDefined) {
    val path = Path(cli.config.getOrElse(""), os.pwd)
    if (!os.exists(path)) {
      println(s"> Config '${path}' not found, using default Parameters")
      HPipeParameters()
    } else {
      val result = toml.Toml.parseAs[HPipeParameters](os.read(path))
      result match {
        case Right(param) => param;
        case Left(e)      => {
          println(s"> Error when parsing config: $e")
          sys.exit(1)
        }
      }
    }
  } else {
    println("> Config not provided, using default Parameters")
    HPipeParameters()
  }

  println()
  println(conf.spy)

  // Generate in the current directory when not specified
  val out         = cli.output.getOrElse(".")
  val wrappedArgs = Array("--target-dir", out)
  val firOpts     = cli.firOpts.map { case (k, v) => s"--$k=$v" }.toArray

  println("> Extra Firtool options")
  if (cli.firOpts.size == 0) println("  (None)")
  cli.firOpts.map(b => println(s"  - ${b._1} = ${b._2}"))
  println()

  println("> Generating SystemVerilog...")
  Export(new HPipe()(conf), wrappedArgs, firOpts)
  println(s"> Generated SystemVerilog in ${Path(out, os.pwd).toString()}")
}
