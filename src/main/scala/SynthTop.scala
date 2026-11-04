import chisel3._
import chisel3.experimental.Analog
import chisel3.experimental.attach
import chisel3.util._

class SynthTopIO extends Bundle {
  val out = Output(Bool())
  val rx = Input(Bool())  // midi rx
  val heldBin = Output(UInt(7.W))
}

// Top module for the Synth
// A voice here is 1 square wave frequency. The voices are toggled by midi input.
// It outputs a bool `io.out`
// The outputs are or-ed together in `io.out` - essentially adding the voices when
// multiple are played.
class SynthTop(
    clockFrequency: Int = Nexys4.clockFrequency
) extends Module {
  val io = IO(new SynthTopIO())

  val midi = Module(
    new MidiTop(clkHz = clockFrequency, baud = Midi.midiBaudRate)
  )
  midi.io.rx := io.rx

  val outs: Vec[Bool] = Wire(Vec(Env.amntFreq, Bool()))

  val outs: Vec[Bool] = Wire(Vec(Env.amntFreq, Bool()))
  var voice_idx = 0
  for (voice <- List(OscillatorType.Sine,OscillatorType.Square,OscillatorType.Saw,OscillatorType.Triangle)) {
    for (i <- 0 until Env.amntFreqPerVoice) {
      val synth = Module(
        new Synth(
          frequency = MidiUtils.midiToFreq(  // set frequency for note based on midi value
            i + Env.rootMidiNote             // default root midi note is 60 i.e. C4 
          ),
          clockFrequency = clockFrequency,
          voice=voice
        )
      )

      // midi linked to 60,61,62,63 for the first voice type, then 64,65,66,67 for the second, etc.
      synth.io.play := midi.io.held(i+voice_idx*Env.amntFreqPerVoice + Env.rootMidiNote)
      outs(i+voice_idx*Env.amntFreqPerVoice) := synth.io.out
    }
    voice_idx += 1
  }

  // Combine all voices. If any voice output is on, output is on.
  // For now this is fine. When changing duty cycle to control volume, we will need to
  // limit it somehow
  io.out := outs.reduce(_ || _)

  io.heldBin := midi.io.data1
}
