import chisel3._
import chiseltest._
import chiseltest.simulator.WriteVcdAnnotation
import org.scalatest.flatspec.AnyFlatSpec

class ModelSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Golden Model"
  it should "work right" in {
    test(new MVP()) { _ =>
      val model = new GoldenModel(1000, List.fill(1000)(4))
      println("done")
    }
  }
}
