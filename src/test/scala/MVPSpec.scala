import chisel3._
import chiseltest._
import chiseltest.simulator.WriteVcdAnnotation
import org.scalatest.flatspec.AnyFlatSpec

class MVPSpec extends AnyFlatSpec with ChiselScalatestTester {
  val clockFrequency = 100_000

  "MVP" should "dump a VCD of playing 1 second" in test(
    new MVP(frequency = 240, clockFrequency = clockFrequency)
  ).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
    dut.clock.setTimeout(0) // no idle-cycle timeout for the long run
    dut.io.play.poke(false.B)
    dut.clock.step(10)

    dut.io.play.poke(true.B)
    dut.clock.step(1)

    // 1 second of wave, then some silence
    dut.clock.step(clockFrequency)
    dut.io.out.expect(false.B)
    dut.clock.step(100)
  }
}
