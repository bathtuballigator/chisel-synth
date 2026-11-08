import chisel3._
import chisel3.util.Cat
import chisel3.util.log2Ceil

class PWMOscillator(
    freq: Int = 440,
    clock_speed: Int = 100_000_000
) extends Module {
  val size: Int = clock_speed / freq
  val width: Int = log2Ceil(size)
  val PWM_in: UInt = (size >> 1).U

  val io = IO(new Bundle {
    val PWM_out = Output(Bool())
  })

  val acc = RegInit(0.U(width.W))
  val PWM_thresh = RegInit((size >> 1).U(width.W))

  acc := Mux(acc === (size - 1).U, 0.U, acc + 1.U)
  PWM_thresh := Mux(acc === 0.U, PWM_in, PWM_thresh)
  io.PWM_out := acc >= PWM_thresh

}

class MVPIO extends Bundle {
  val play = Input(Bool())
  val frequencyHz = Input(UInt(32.W)) // for dynamic frequency control
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

