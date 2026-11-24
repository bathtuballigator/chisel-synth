import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SynthConfigHardwareSpec extends AnyFlatSpec
    with ChiselScalatestTester with Matchers {
  "The configured note bank" should "use the selected note count and highest pitch" in {
    val clockFrequency = 100_000
    val config = SynthConfig.load(java.nio.file.Paths.get("src/test/resources/four-notes.env"))
    val frequency = MidiUtils.midiToFreq(config.rootMidiNote + config.amountFreq - 1)
    val period = clockFrequency / frequency

    test(new MVP_Top(clockFrequency, config)) { dut =>
      dut.clock.setTimeout(0)
      dut.io.sel.length shouldBe config.amountFreq
      for (index <- 0 until config.amountFreq) {
        dut.io.sel(index).poke(false.B)
      }
      dut.clock.step(2)
      dut.io.out.expect(false.B)

      // Select the last configured note and measure one complete steady period.
      dut.io.sel(config.amountFreq - 1).poke(true.B)
      dut.clock.step(period + 2)
      var highCycles = 0
      for (_ <- 0 until period) {
        if (dut.io.out.peek().litToBoolean) highCycles += 1
        dut.clock.step()
      }
      highCycles shouldBe (period + 1) / 2

      dut.io.sel(config.amountFreq - 1).poke(false.B)
      dut.io.out.expect(false.B)
    }
  }
}
