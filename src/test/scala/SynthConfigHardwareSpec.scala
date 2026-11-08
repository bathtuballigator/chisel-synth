import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SynthConfigHardwareSpec extends AnyFlatSpec
    with ChiselScalatestTester with Matchers {
  "The configured note bank" should "use the selected note count and highest pitch" in {
    val clockFrequency = 100_000
    val frequency = MidiUtils.midiToFreq(Env.rootMidiNote + Env.amntFreq - 1)
    val period = clockFrequency / frequency

    test(new MVP_Top(clockFrequency)) { dut =>
      dut.clock.setTimeout(0)
      dut.io.sel.length shouldBe Env.amntFreq
      for (index <- 0 until Env.amntFreq) {
        dut.io.sel(index).poke(false.B)
      }
      dut.clock.step(2)
      dut.io.out.expect(false.B)

      // Select the last configured note and measure one complete steady period.
      dut.io.sel(Env.amntFreq - 1).poke(true.B)
      dut.clock.step(period + 2)
      var highCycles = 0
      for (_ <- 0 until period) {
        if (dut.io.out.peek().litToBoolean) highCycles += 1
        dut.clock.step()
      }
      highCycles shouldBe (period + 1) / 2

      dut.io.sel(Env.amntFreq - 1).poke(false.B)
      dut.io.out.expect(false.B)
    }
  }
}
