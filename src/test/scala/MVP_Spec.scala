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
) extends MVP(amp, sampleWid, phaseWid, frequency, sampleRate) {
  val PWM_outExposed = expose(pwm.io.PWM_out)
  val sampleExposed = expose(squareOsc.io.sample)
  val dutyCycleExposed = expose(dutyCycle)
  val sampleOrSilenceExposed = expose(sampleOrSilence)
  val pwmFrequencyExposed = sampleRate * 10
}

class MVP_Spec extends AnyFlatSpec with ChiselScalatestTester {
  "MVP" should "sanity check" in test(new MVP_Exposed(5, 4, 4, 4, 16)) {
    dut =>
    dut.io.play.poke(true.B)
    dut.clock.step()
    var sampleVal = 5
    var dutyCycleVal = (sampleVal + (2 << (4 - 1)))
    for (i <- 0 until 4) { // what should end val be??
      for (j <- 0 until dut.pwmFrequencyExposed) {
        println((i,j))
        dut.sampleExposed.expect(sampleVal.S)
        dut.dutyCycleExposed.expect(dutyCycleVal.U)
        dut.io.out.expect((j >= dutyCycleVal).B)
        dut.clock.step()
      }
      sampleVal = -sampleVal
      dutyCycleVal = (sampleVal + (2 << (4 - 1)))
    }
  }
  it should "Have high impedance instead of 1" in test(new MVP_Exposed()) { // ??
    dut =>
  }
  it should "Have out 0 when deasserting play" in test(new MVP_Exposed()) {
    dut => // should it be 0 or follow clock???
    dut.io.play.poke(false.B)
    dut.clock.step()
    for (i <- 0 until 10) {
      dut.io.out.expect(false.B)
      dut.clock.step()
    }
  }

}
