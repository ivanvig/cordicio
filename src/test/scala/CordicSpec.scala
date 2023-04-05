package cordicio

import chisel3._
import chiseltest._

import breeze.linalg._
import breeze.numerics._
import breeze.numerics.constants.Pi
import breeze.plot._

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ArrayBuffer

class CordicTest extends AnyFlatSpec with ChiselScalatestTester with Matchers{
  behavior of "Cordic"
  it should "generate a sin wave" in {
    test(new IterativeCordic(16, 10, 10)).withAnnotations(Seq(WriteVcdAnnotation)) { dut => 
      val phi = DenseVector.tabulate(40){x => 2.0*Pi*x/40}

      val res_sin = ArrayBuffer[Double]()
      val res_cos = ArrayBuffer[Double]()

      dut.io.in_start.poke(true.B)

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

        res_cos += dut.io.out_x.peekInt().toDouble/(1.6467605*pow(2,10))
        res_sin += dut.io.out_y.peekInt().toDouble/(1.6467605*pow(2,10))
      }

      val res_vec_sin = DenseVector(res_sin.toArray)
      val res_vec_cos = DenseVector(res_cos.toArray)
      val f = Figure()
      val p = f.subplot(0)
      p += plot(phi, res_vec_sin, '.')
      p += plot(phi, sin(phi))
      p.xlabel = "x axis"
      p.ylabel = "y axis"
      val p1 = f.subplot(2,1,1)
      p1 += plot(phi, res_vec_cos, '.')
      p1 += plot(phi, cos(phi))
      p1.xlabel = "x axis"
      p1.ylabel = "y axis"


      scala.io.StdIn.readLine()
      //f.refresh()
      //val f = Figure()
      //val p = f.subplot(0)
      //p += plot(x, sin(x))
      //p.xlabel = "x axis"
      //p.ylabel = "y axis"
      //f.saveas("lines.png") // save current figure as a .png, eps and pdf also supported
    }
  }
}
