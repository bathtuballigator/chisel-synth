import chisel3._
import chisel3.experimental.Analog
import chisel3.experimental.attach
import chisel3.util._

class MVP_TopIO extends Bundle {
  val out = Output(Bool())
  val sel = Input(Vec(Env.amntFreq, Bool()))
}

// Top module for MVP voices
// A voice here is 1 square wave frequency. The voices are toggled by sel signal.
// It outputs a bool `io.out`
// The outputs are or-ed together in `io.out` - essentially adding the voices when
// multiple are played.
class MVP_Top(
    clockFrequency: Int = Nexys4.clockFrequency
    // TODO: Add param to toggle - if voices should be or-ed or handle sel as one hot.
    // Could it even be a function as a param? :O
) extends Module {
  val io = IO(new MVP_TopIO())

  val outs: Vec[Bool] = Wire(Vec(Env.amntFreq, Bool()))

  for (i <- 0 until Env.amntFreq) {
    val mvp = Module(
      new MVP(
        frequency = MidiUtils.midiToFreq(  // set frequency for note based on midi value
          i + Env.rootMidiNote             // default root midi note is 60 i.e. C4 
        ),
        clockFrequency = clockFrequency
      )
    )
    mvp.io.play := io.sel(i)  // enable voice if selected
    outs(i) := mvp.io.out
  }

  // Combine all voices. If any voice output is on, output is on.
  // For now this is fine. When changing duty cycle to control volume, we will need to
  // limit it somehow
  io.out := outs.reduce(_ || _)

}
