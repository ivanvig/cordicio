package cordicio

import chisel3._
import chisel3.util._
import scala.math


object Func {
  def generate_angles(n_stages: Int): Seq[Double] = {
    (0 until n_stages).map(x => math.atan(1/math.pow(2,x)))
  }
}

object Mode extends ChiselEnum {
  val RM, VM = Value
}

class IterativeCordic(in_width: Int, in_frac: Int, n_stages: Int) extends Module {
  val io = IO(new Bundle {
    val in_x        = Input(SInt(in_width.W))
    val in_y        = Input(SInt(in_width.W))
    val in_z        = Input(SInt(in_width.W))
    val in_mode     = Input(Mode())
    val in_start    = Input(Bool())

    val out_x       = Output(SInt(in_width.W))
    val out_y       = Output(SInt(in_width.W))
    val out_z       = Output(SInt(in_width.W))
    val out_busy    = Output(Bool())
  })
  val angles_rom = VecInit(Func.generate_angles(n_stages).map(
    x => math.round(x*math.pow(2,in_width)/(2*math.Pi)).asSInt(in_width.W))
  )

  // 2**w-2 = 1/4 2**w, where 2**w = 2pi
  val pi_over_two = math.pow(2,in_width-2).toInt.asSInt(in_width.W)

  val running = RegInit(false.B)

  val x_reg = Reg(chiselTypeOf(io.in_x))
  val y_reg = Reg(chiselTypeOf(io.in_y))
  val z_reg = Reg(chiselTypeOf(io.in_z))

  val sign = Mux(io.in_mode.asUInt.asBool, ~y_reg.head(1).asBool, z_reg.head(1).asBool)
  val in_sign = Mux(io.in_mode.asUInt.asBool, ~io.in_y.head(1).asBool, io.in_z.head(1).asBool)

  val preproc_x = Mux(in_sign, io.in_y, -io.in_y)
  val preproc_y = Mux(in_sign, -io.in_x, io.in_x)
  val adjustment = Mux(in_sign, pi_over_two, -pi_over_two)

  val (stage, last_stage) = Counter(running, n_stages)

  val ang = angles_rom(stage)

  val shifted_x = x_reg >> stage
  val shifted_y = y_reg >> stage

  val sign_x   = Mux(sign, -shifted_x, shifted_x)
  val sign_y   = Mux(sign, shifted_y, -shifted_y)
  val sign_ang = Mux(sign, ang, -ang)

  x_reg       := Mux(running, x_reg + sign_y, preproc_x)
  y_reg       := Mux(running, y_reg + sign_x, preproc_y)
  z_reg       := Mux(running, z_reg + sign_ang, io.in_z + adjustment)

  running     := (io.in_start | running) & ~last_stage

  io.out_busy := running
  io.out_x    := x_reg
  io.out_y    := y_reg
  io.out_z    := z_reg
}

object Main extends App {
  // These lines generate the Verilog output
  new (chisel3.stage.ChiselStage).emitVerilog(
    new IterativeCordic(16,10,15),
    Array(
      "--emission-options=disableMemRandomization,disableRegisterRandomization"
    )
  )
}
