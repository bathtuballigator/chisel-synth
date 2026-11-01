import chisel3._
import chiseltest._
import chiseltest.simulator.WriteVcdAnnotation
import org.scalatest.flatspec.AnyFlatSpec
import chisel3.simulator.PeekPokeAPI

class ModelSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Golden Model"
  it should "work right" in {
    test(new MVP()) { dut =>
      val simSteps = 10_000_000
      val model = new GoldenModel(Nexys4.clockFrequency, simSteps, 0)
      dut.io.play.poke(true.B)
      dut.clock.setTimeout(0)
      for (i <- 0 until simSteps) {
        dut.clock.step()
        dut.io.out.expect(model.step(0))
      }
    }
  }
}
