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
  val widthExposed = width
  val maxValExposed = maxVal
  val pwmWidthExposed = pwm.width

  val amp_ = amp
  val sampleWid_ = sampleWid
  val phaseWid_ = phaseWid
  val frequency_ = frequency
  val sampleRate_ = sampleRate

}

class MVP_Spec extends AnyFlatSpec with ChiselScalatestTester {
  "MVP" should "sanity check" in test(new MVP_Exposed(5, 4, 4, 4, 16)) { dut =>
    dut.io.play.poke(true.B)

    var phaseSize = (1 << dut.phaseWid_) //

    var increment = (dut.frequency_ * phaseSize) / dut.sampleRate_
    var period = (1 << dut.phaseWid_) / increment

    var sampleVal = 5
    var dutyCycleCalc =
      (sampleVal: Int) => (sampleVal + (1 << (dut.sampleWid_ - 1)))
    var dutyCycleVal = dutyCycleCalc(sampleVal) // sample + 2^(N-1)

    for (i <- 0 until 8) { // what should end val be??
      for (j <- 0 until period / 2) {
        val rowCol = (
          (i, j),
          dut.sampleExposed.peekInt(),
          dut.dutyCycleExposed.peekInt(),
          dut.io.out.peekBoolean()
        ).toString()

        dut.sampleExposed.expect(sampleVal.S, rowCol)
        dut.dutyCycleExposed.expect(dutyCycleVal.U, rowCol)
        dut.io.out.expect((j >= dutyCycleVal).B, rowCol)
        dut.clock.step()
      }
      sampleVal = -sampleVal
      dutyCycleVal = dutyCycleCalc(sampleVal)
    }
  }

  it should "Have out 0 when deasserting play" in test(new MVP_Exposed()) {
    dut => // should it be 0 or follow clock???
      dut.io.play.poke(false.B)
      dut.clock.step()
      for (i <- 0 until dut.maxValExposed + 1) {
        dut.io.out.expect(false.B, s"$i")
        dut.clock.step()
      }
  }

}
