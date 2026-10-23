import chisel3._
import chisel3.util.Cat
import chisel3.util.log2Ceil

class MVPIO extends Bundle {
  val play = Input(Bool())
  val out = Output(Bool())
}

class MVP(
    frequency: Int = 440, // Frequency to play square wave in Hz,
    clockFrequency: Int = Nexys4.clockFrequency, // Hz
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

object MVP extends App {
  // The board build needs 16 sample bits for the existing 48 kHz requirement.
  // Keep the module defaults and test configurations unchanged.
  val verilog = emitVerilog(
    new MVP(frequency = 440, clockFrequency = 100_000_000)
  ).split(
    "// ----- 8< ----- FILE \"firrtl_black_box_resource_files.f\""
  ) // a bit hacky
    .head // drop the trailing file list, which is not valid SystemVerilog
  val output = java.nio.file.Paths.get("generated", "MVP.sv")
  java.nio.file.Files.createDirectories(output.getParent)
  java.nio.file.Files
    .write(output, verilog.getBytes(java.nio.charset.StandardCharsets.UTF_8))
  println(s"Vivado source written to ${output.toAbsolutePath}")
}
