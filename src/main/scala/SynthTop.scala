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

  for (i <- 0 until Env.amntFreq) {
    val mvp = Module(
      new MVP(
        frequency = MidiUtils.midiToFreq(
          i + Env.rootMidiNote
        ),
        clockFrequency = clockFrequency
      )
    )

    // We currently only support one frequency per module
    mvp.io.play := midi.io.held(i + Env.rootMidiNote)
    outs(i) := mvp.io.out
  }

  // Combine all voices. If any voice output is on, output is on.
  // For now this is fine. When changing duty cycle to control volume, we will need to
  // limit it somehow
  io.out := outs.reduce(_ || _)

  io.heldBin := midi.io.data1
}
