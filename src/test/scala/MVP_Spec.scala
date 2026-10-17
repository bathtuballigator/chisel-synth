import chisel3._
import chiseltest._
import chiseltest.experimental.expose
import org.scalatest.flatspec.AnyFlatSpec

class MVP_Exposed(
    amp: Int = 10,
    sampleWid: Int = 8,
    phaseWid: Int = 4,
    frequency: Int = 440,
    sampleRate: Int = 48_000
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
  it should "sanity check" in test(new MVP_Exposed(1, 2) {
    dut =>
  })
  it should "Have high impedance instead of 1" in test(new MVP_Exposed() { // ??
    dut =>
  })
  it should "Have out 0 when deasserting play" in test(new MVP_Exposed() {
    dut =>
  })

}
