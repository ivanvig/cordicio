package cordicio

import chisel3._
import chiseltest._

import breeze.linalg._
import breeze.math.Complex.i
import breeze.numerics._
import breeze.numerics.constants.Pi
import breeze.plot._

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ArrayBuffer

class CordicTest extends AnyFlatSpec with ChiselScalatestTester with Matchers{
  val cordic_gain = 1.64676025812107
  behavior of "Cordic"
  it should "generate a sin and cos wave" in {
    test(new IterativeCordic(16, 10, 20)).withAnnotations(Seq(WriteVcdAnnotation)) { dut => 
      val phi = DenseVector.tabulate(20){x => 2.0*Pi*x/20}

      dut.io.in_start.poke(true.B)
      dut.io.in_mode.poke(Mode.RM)

      phi.foreach { ang => 
        // Poor's man Two Complement modular arithmetic
        val modang = ang%(2*Pi)
        val zval = if (modang < Pi) {
          modang
        } else {
          modang - 2*Pi
        }

        dut.io.in_x.poke(round(1*pow(2, 10)).S(16.W))
        dut.io.in_y.poke(0.S(16.W))
        dut.io.in_z.poke(round(zval*pow(2, 16)/(2*Pi)))

        dut.clock.step()
        dut.clock.step()

        while( dut.io.out_busy.peekBoolean() ) { dut.clock.step() }

        dut.io.out_x.peekInt().toDouble/(cordic_gain*pow(2,10)) should be (cos(ang) +- 0.01)
        dut.io.out_y.peekInt().toDouble/(cordic_gain*pow(2,10)) should be (sin(ang) +- 0.01)
      }
    }
  }

  it should "rotate a vector" in {
    test(new IterativeCordic(16, 10, 20)).withAnnotations(Seq(WriteVcdAnnotation)) { dut => 
      val num = 1+1*i
      val ang = -Pi/2

      dut.io.in_start.poke(true.B)
      dut.io.in_mode.poke(Mode.RM)

      dut.io.in_x.poke(round(num.real*pow(2, 10)).S(16.W))
      dut.io.in_y.poke(round(num.imag*pow(2, 10)).S(16.W))
      dut.io.in_z.poke(round(ang*pow(2, 16)/(2*Pi)))

      dut.clock.step()

      while( dut.io.out_busy.peekBoolean() ) { dut.clock.step() }

      dut.io.out_x.peekInt().toDouble/(cordic_gain*pow(2,10)) should be (1.0 +- 0.01)
      dut.io.out_y.peekInt().toDouble/(cordic_gain*pow(2,10)) should be (-1.0 +- 0.01)
    }
  }

  it should "convert from polar to cartesian" in {
    test(new IterativeCordic(16, 10, 20)).withAnnotations(Seq(WriteVcdAnnotation)) { dut => 
      1 to 10 foreach {_ => 
        val mag = scala.util.Random.between(0, 1.5)
        val ang = scala.util.Random.between(0, 2*Pi)
        val expected = mag * exp(ang*i)

        val modang = ang%(2*Pi)
        val zval = if (modang < Pi) {
          modang
        } else {
          modang - 2*Pi
        }

        dut.io.in_start.poke(true.B)
        dut.io.in_mode.poke(Mode.RM)

        dut.io.in_x.poke(round(mag*pow(2, 10)).S(16.W))
        dut.io.in_y.poke(0.S(16.W))
        dut.io.in_z.poke(round(zval*pow(2, 16)/(2*Pi)))

        dut.clock.step()

        while( dut.io.out_busy.peekBoolean() ) { dut.clock.step() }

        dut.io.out_x.peekInt().toDouble/(cordic_gain*pow(2,10)) should be (expected.real +- 0.01)
        dut.io.out_y.peekInt().toDouble/(cordic_gain*pow(2,10)) should be (expected.imag +- 0.01)

      }
    }
  }     

  it should "calculate arctan function" in {
    test(new IterativeCordic(16, 10, 20)).withAnnotations(Seq(WriteVcdAnnotation)) { dut => 
      1 to 10 foreach {_ => 
        val num = scala.util.Random.between(-1.5, 1.5)

        dut.io.in_start.poke(true.B)
        dut.io.in_mode.poke(Mode.VM)

        dut.io.in_x.poke(round(1*pow(2, 10)).S(16.W))
        dut.io.in_y.poke(round(num*pow(2, 10)).S(16.W))
        dut.io.in_z.poke(0.S(16.W))

        dut.clock.step()

        while( dut.io.out_busy.peekBoolean() ) { dut.clock.step() }

        ((dut.io.out_z.peekInt().toDouble*2*Pi)/(pow(2,16))) should be (atan(num) +- 0.01)

      }
    }
  }     
  it should "convert from cartesian to polar" in {
    test(new IterativeCordic(16, 10, 20)).withAnnotations(Seq(WriteVcdAnnotation)) { dut => 
      1 to 10 foreach { _ => 
        val expected_mag = scala.util.Random.between(0, 1.5)
        val expected_ang = scala.util.Random.between(-Pi, Pi)
        val num = expected_mag * exp(expected_ang*i)

        dut.io.in_start.poke(true.B)
        dut.io.in_mode.poke(Mode.VM)

        dut.io.in_x.poke(round(num.real*pow(2, 10)).S(16.W))
        dut.io.in_y.poke(round(num.imag*pow(2, 10)).S(16.W))
        dut.io.in_z.poke(0.S(16.W))

        dut.clock.step()

        while( dut.io.out_busy.peekBoolean() ) { dut.clock.step() }

        dut.io.out_x.peekInt().toDouble/(cordic_gain*pow(2,10)) should be (expected_mag +- 0.01)
        (2*Pi*dut.io.out_z.peekInt().toDouble/pow(2,16)) should be (expected_ang +- 0.01)
      }
    }
  }     
}
