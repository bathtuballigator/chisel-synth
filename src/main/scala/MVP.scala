import chisel3._
import chisel3.util.log2Ceil
import chisel3.util.Cat

class MVP(
    amp: Int = 256,
    sampleWid: Int = 8,
    phaseWid: Int = 16,
    frequency: Int = 440 // Frequency to play square wave in Hz
) extends Module {
  val io = IO(
    new Bundle {
      val play = Input(Bool()) // Whether to play sound or not
      val out = Output(
        Bool()
      ) // Linked to AUD_PWM  // TODO: FIXME: Should be tristate floating instead of 1
    }
  )
  val sampleRate = 48000 // 48kHz
  val pwmFrequency = sampleRate * 10
  require(log2Ceil(sampleRate) <= sampleWid)
  require(frequency >= 0, "Frequency must be non-negative")
  require(
    frequency < sampleRate / 2,
    "Frequency must be less than Nyquist frequency"
  )
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
  val sampleOrSilence = // sample or 0% duty cycle
    Mux(io.play, dutyCycle, 0.U)
  pwm.io.PWM_in := sampleOrSilence // ???
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
