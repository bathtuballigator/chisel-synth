import chisel3._
import chisel3.util.Cat
import chisel3.util.log2Ceil

class MVP(
    amp: Int = 256,
    sampleWid: Int = 8,
    phaseWid: Int = 16,
    frequency: Int = 440, // Frequency to play square wave in Hz,
    sampleRate: Int = 48_000,
    clockFrequency: Int = 100_000_000 // Hz
) extends Module {
  val io = IO(
    new Bundle {
      val play = Input(Bool()) // Whether to play sound or not
      val out = Output(
        Bool()
      ) // Linked to AUD_PWM  // TODO: FIXME: Should be tristate floating instead of 1??
    }
  )
  val pwmFrequency: Int = sampleRate * 10
  val width: Int = log2Ceil(pwmFrequency)
  val maxVal: Int = (2 << width) - 1

  require(log2Ceil(sampleRate) <= sampleWid)
  require(frequency >= 0, "Frequency must be non-negative")
  require(
    frequency < sampleRate / 2,
    "Frequency must be less than Nyquist frequency"
  )
  val squareOsc =
    Module(
      new PWMOscillator(
        freq = frequency,
        clock_speed = clockFrequency
      )
    )

  val sampleOrSilence = ( // sample or 0% duty cycle
    Mux(
      io.play,
      squareOsc.io.PWM_out,
      0.U
    ) // be off for all cycles when no play
  )
  io.out := sampleOrSilence
}

object MVPConfig {
  def sampleToDutyCycle(sample: SInt, sampleWid: Int): UInt = {
    // N = sampleWid
    // sample in [-2^(N-1), 2^(N-1) - 1]
    // normedSample = (sample + 2^(N-1))
    // normedSample in [0, 2^(N) - 1]
    // duty = normedSample

    // Magic trick for computing `sample + 2^(N-1)`
    val duty = Cat(~sample(sampleWid - 1), sample(sampleWid - 2, 0))
    duty
  }

}

object MVP extends App {
  // The board build needs 16 sample bits for the existing 48 kHz requirement.
  // Keep the module defaults and test configurations unchanged.
  val verilog = emitVerilog(new MVP(amp = 256, sampleWid = 16))
  val output = java.nio.file.Paths.get("generated", "MVP.sv")
  java.nio.file.Files.createDirectories(output.getParent)
  java.nio.file.Files
    .write(output, verilog.getBytes(java.nio.charset.StandardCharsets.UTF_8))
  println(s"Vivado source written to ${output.toAbsolutePath}")
}
