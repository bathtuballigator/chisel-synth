import chisel3._
import chisel3.util.Cat
import chisel3.util.log2Ceil

class MVPIO extends Bundle {
  val play = Input(Bool())
  val frequencyHz = Input(UInt(32.W)) // for dynamic frequency control
  val out = Output(Bool())
}

class MVP(
    frequency: Int = 440, // Frequency to play square wave in Hz,
    clockFrequency: Int = Nexys4.clockFrequency, // Hz
    voice: OscillatorType = OscillatorType.Square
) extends Module {
  val io = IO(new MVPIO())

  val osc =
    Module(
      new PWMGenerator(
        // freq = frequency,
        clock_speed = clockFrequency,
        voice = voice
      )
    )
    
  osc.io.PWM_en := io.play
  osc.io.frequencyHz := io.frequencyHz
  io.out := osc.io.PWM_out
}
