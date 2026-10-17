import chisel3._
import chiseltest._
import chiseltest.experimental.expose
import org.scalatest.flatspec.AnyFlatSpec

// increment = (frq * 2^phaseWid) / sampleRate = 4 * 16 / 16 = 4
// period = 2^phaseWid / increment = 16/4 = 4 steps, half-period 2
// sample over time = +5,+5,-5,-5,+5,...=0101,0101,1011,1011,0101,...
// dutyCycle over time -> flip MSB -> 1101,1101,0011,...
class MVP_Exposed(
    amp: Int = 5,
    sampleWid: Int = 4,
    phaseWid: Int = 4,
    frequency: Int = 4,
    sampleRate: Int = 16
) extends MVP(amp, sampleWid, phaseWid, frequency) {
  val PWM_outExposed = expose(pwm.io.PWM_out)
  val sampleExposed = expose(squareOsc.io.sample)
  val dutyCycleExposed = expose(dutyCycle)
  val sampleOrSilenceExposed = expose(sampleOrSilence)
}

class MVPSpec extends AnyFlatSpec with ChiselScalatestTester {
  "MVP" should "Play 440hz sq. wav when asserting play" in test(
    new MVP_Exposed(10, 8, 4, 440) {
      dut =>
      dut.io.play.poke(true.B)
      dut.clock.step()
      dut.io.out.expect()

    }
  )
  it should "sanity check" in test(new MVP_Exposed() {
    dut =>
      dut.io.play.poke(true.B)
      dut.clock.step()
      var sampleVal = 5
      dut.sampleExposed.expect(sampleVal.S)
      dut.dutyCycleExposed.expect((sampleVal).S)
      dut.clock.step()
      dut.sampleExposed.expect(5.S)
      dut.clock.step()
      dut.sampleExposed.expect(-5.S)
      dut.clock.step()
      dut.sampleExposed.expect(-5.S)
      dut.io.out.expect()
  })
  it should "Have high impedance instead of 1" in test(new MVP_Exposed() { // ??
    dut =>
  })
  it should "Have out 0 when deasserting play" in test(new MVP_Exposed() {
    dut =>
  })

}
