import chisel3._

class SynthIO(sampleWid: Int) extends Bundle {
  val frequencyHz = Input(UInt(32.W)) // Frequency to play in Hz
  val gate = Input(Bool())
  val sample=Output(SInt(sampleWid.W))
  val active = Output(Bool())
}

// synth module produces a single signed sample based on input frequency
class Synth(
    sampleWid: Int = 8, // default to 8 bit
    clockFrequency: Int = Nexys4.clockFrequency, // Hz
    voice: OscillatorType = OscillatorType.Square,
    envelope: AdsrConfig = AdsrConfig()
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
    
  val active = if (envelope.enabled) {
    val adsr = Module(new ADSREnvelope(
      envelope.attackMs, envelope.decayMs, envelope.sustain.toDouble,
      envelope.releaseMs, clockHz = clockFrequency
    ))
    adsr.io.gate := io.gate
    val level = adsr.io.level
    // Widen the unsigned gain before treating it as signed. This keeps the
    // oscillator's negative samples negative and maps full gain to unity.
    val fullScale = ((BigInt(1) << level.getWidth) - 1).S
    val scaled = (osc.io.sample * level.zext) / fullScale
    io.sample := Mux(adsr.io.active, scaled, 0.S)
    adsr.io.active
  } else {
    io.sample := Mux(io.gate, osc.io.sample, 0.S)
    io.gate
  }
  osc.io.frequencyHz := Mux(io.gate || active, io.frequencyHz, 0.U)
  io.active := active
}
