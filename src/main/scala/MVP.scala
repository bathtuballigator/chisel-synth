import chisel3._
import chisel3.util.Cat
import chisel3.util.log2Ceil
import chisel3.experimental.{Analog, attach}
import chisel3.util.HasBlackBoxInline

// Open-drain driver for AUD_PWM: pulls low on 0, floats (high-Z) on 1.
class MVPBlackBox extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val in_AUD_PWM = Input(Bool())
    val AUD_PWM = Analog(1.W)
  })

  setInline(
    "MVPBlackBox.sv",
    s"""
    |module MVPBlackBox(
    |  input  in_AUD_PWM,
    |  inout  AUD_PWM
    |);
    |  assign AUD_PWM = in_AUD_PWM ? 1'bz : 1'b0;
    |endmodule
    """.stripMargin
  )
}

class MVP(
    frequency: Int = 440, // Frequency to play square wave in Hz,
    clockFrequency: Int = 100_000_000 // Hz
) extends Module {
  val io = IO(
    new Bundle {
      val play = Input(Bool()) // Whether to play sound or not
      val AUD_SD = Output(Bool()) // High signal for the AUD_SD
      val out = Analog(
        1.W
      ) // tri-state inspired by https://www.fabienm.eu/flf/manage-high-impedance-z-state-with-chisel/
      // Linked to AUD_PWM: 0 when low, high-Z when high
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
  val pwmDriver = Module(new MVPBlackBox)
  pwmDriver.io.in_AUD_PWM := sampleOrSilence
  attach(pwmDriver.io.AUD_PWM, io.out)
}

object MVP extends App {
  // The board build needs 16 sample bits for the existing 48 kHz requirement.
  // Keep the module defaults and test configurations unchanged.
  val verilog = emitVerilog(
    new MVP(frequency = 440, clockFrequency = 100_000_000)
  ).split("// ----- 8< ----- FILE \"firrtl_black_box_resource_files.f\"")  // a bit hacky
    .head // drop the trailing file list, which is not valid SystemVerilog
  val output = java.nio.file.Paths.get("generated", "MVP.sv")
  java.nio.file.Files.createDirectories(output.getParent)
  java.nio.file.Files
    .write(output, verilog.getBytes(java.nio.charset.StandardCharsets.UTF_8))
  println(s"Vivado source written to ${output.toAbsolutePath}")
}
