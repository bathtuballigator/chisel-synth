import chisel3._
import chisel3.util.Cat
import chisel3.util.log2Ceil

class MVP(
    frequency: Int = 440, // Frequency to play square wave in Hz,
    clockFrequency: Int = 100_000_000 // Hz
) extends Module {
  val io = IO(
    new Bundle {
      val play = Input(Bool()) // Whether to play sound or not
      val AUD_SD = Output(Bool()) // High signal for the AUD_SD
      val out = Output(
        Bool()
      ) // Linked to AUD_PWM  // TODO: FIXME: Should be tristate floating instead of 1??
    }
  )
  io.AUD_SD := true.B // Default to high for AUD_SD


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

object MVP extends App {
  // The board build needs 16 sample bits for the existing 48 kHz requirement.
  // Keep the module defaults and test configurations unchanged.
  val verilog = emitVerilog(new MVP(frequency = 440, clockFrequency = 100_000_000))
  val output = java.nio.file.Paths.get("generated", "MVP.sv")
  java.nio.file.Files.createDirectories(output.getParent)
  java.nio.file.Files
    .write(output, verilog.getBytes(java.nio.charset.StandardCharsets.UTF_8))
  println(s"Vivado source written to ${output.toAbsolutePath}")
}
