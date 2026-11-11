import chisel3._
import chisel3.util.Cat
import chisel3.util.log2Ceil

class SynthIO(sampleWid: Int) extends Bundle {
  val frequencyHz = Input(UInt(32.W)) // Frequency to play in Hz
  val sample=Output(SInt(sampleWid.W))
}

// synth module produces a single signed sample based on input frequency
class Synth(
    sampleWid: Int = 8, // default to 8 bit
    clockFrequency: Int = Nexys4.clockFrequency, // Hz
    voice: OscillatorType = OscillatorType.Square
) extends Module {
  val io = IO(new SynthIO(sampleWid))

  val osc =
    Module(
      new Oscillator(
        sampleWid = sampleWid,
        clkFreq = clockFrequency,
        oscType = voice,
        amp=((1 << (sampleWid - 1)) - 1),
        phaseWid=32
      )
    )
    
  osc.io.frequencyHz := io.frequencyHz
  io.sample := osc.io.sample
}
