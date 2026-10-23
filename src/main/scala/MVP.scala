import chisel3._
import chisel3.util.Cat
import chisel3.util.log2Ceil

class MVPIO extends Bundle {
  val play = Input(Bool())
  val out = Output(Bool())
}

class MVP(
    frequency: Int = 440, // Frequency to play square wave in Hz,
    clockFrequency: Int = Nexys4.clockFrequency // Hz
) extends Module {
  val io = IO(new MVPIO())

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
