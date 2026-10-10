import chisel3._
import chisel3.util.log2Ceil

class MVP(
    amp: Int = 256,
    phaseWid: Int = 16,
    sampleWid: Int = 8,
    pwmFrequency: Int
) extends Module {
  val io = IO(
    new Bundle {
      val on = Input(Bool()) // Whether to play sound or not
      val out = Output(
        Bool()
      ) // Linked to AUD_PWM  // TODO: FIXME: Should be tristate floating instead of 1
    }
  )
  val sampleRate = 48000 // 48kHz
  val frequency = 440 // 440Hz
  require(log2Ceil(sampleRate) <= sampleWid)

  val squareOsc =
    new SquareOscillator(amp = amp, sampleWid = sampleWid, phaseWid = phaseWid)

  val pwm = new PWM(pwmFrequency)

  squareOsc.io.increment := OscConfig
    .calcIncrement(
      freq = frequency,
      sampleRate = sampleRate,
      phaseWid = phaseWid
    )
    .U
  val dutyCycle =
    MVPConfig.sampleToDutyCycle( // what should happen with sample???
      squareOsc.io.sample,
      sampleWid
    )
  val silenceOrSample = // sample or 50% duty cycle
    Mux(io.on, dutyCycle, 1.U)
  pwm.io.PWM_in := silenceOrSample // ???
  io.out := pwm.io.PWM_out
}

object MVPConfig {
  def sampleToDutyCycle(sample: SInt, sampleWid: Int): UInt = {

    var sampleUSize = (BigInt(1) << sampleWid)
    var signedMax = sampleUSize >> 1

    // N = sampleWid
    // sample in [-2^(N-1), 2^(N-1) - 1]
    // normedSample = (sample + 2^(sampleWid-1))
    // normedSample in [0, 2^(N) - 1]
    // duty = normedSample / 2^N

    val duty = Cat(~sample(sampleWid - 1), sample(sampleWid - 2, 0))
    duty
  }
}
/*

10000
01000

 */
