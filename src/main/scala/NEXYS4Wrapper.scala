import chisel3._
import chisel3.experimental.Analog
import chisel3.experimental.attach
import chisel3.util.HasBlackBoxInline
import chisel3.util._

// 2-FF synchronizer + level debouncer. Output changes only after the
// synchronized input has held its new value for `stableCycles` cycles.
// Example usage:
// 10 ms at 100MHz is more than plenty
// val debounceCycles = 1_000_000
// val play = Module(new Debouncer(debounceCycles))
// play.io.in := io.play
// mvp.play := play.io.out
class Debouncer(stableCycles: Int) extends Module {
  require(stableCycles >= 2)
  val io = IO(new Bundle {
    val in = Input(Bool())
    val out = Output(Bool())
  })

  val sync1 = RegNext(io.in, false.B)
  val sync2 = RegNext(sync1, false.B)

  val stable = RegInit(false.B)
  val cnt = RegInit(0.U(log2Ceil(stableCycles).W))

  when(sync2 === stable) {
    cnt := 0.U
  }.elsewhen(cnt === (stableCycles - 1).U) {
    stable := sync2
    cnt := 0.U
  }.otherwise {
    cnt := cnt + 1.U
  }

  io.out := stable
}

// Open-drain driver for AUD_PWM: pulls low on 0, floats (high-Z) on 1.
// inspired by https://www.fabienm.eu/flf/manage-high-impedance-z-state-with-chisel/
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

class MVPIONexys4 extends Bundle {
  val AUD_SD = Output(Bool())
  val AUD_PWM = Analog(1.W) // Linked to AUD_PWM: 0 when low, high-Z when high
  val sel = Input(Vec(Env.amntFreq, Bool()))
}

// Handles the Nexys4 board connections
// The board is reset active low. The AUD_PWM is required to be driven correctly.
// This is handled here
// inputs:
//   - io.core.sel: Vec(Bool)  // selects which voice to play
// outputs:
//  - AUD_PWM: high-Z or 0  // connected to the AUD_PWM port
//  - AUD_SD: Bool  // always high
class Nexys4Wrapper extends RawModule {
  val clock = IO(Input(Clock()))
  val reset = IO(Input(Bool()))

  // The nexys4 is reset active low, so we have to invert the reset
  withClockAndReset(clock, if (Nexys4.resetActiveLow) !reset else reset) {
    val mvp = Module(
      new MVP_Top(
        clockFrequency = Nexys4.clockFrequency
      )
    )
    val io = IO(new MVPIONexys4())
    io.AUD_SD := true.B // Required by spec

    mvp.io.sel := io.sel

    // Set AUD_PWM to be high-Z instead of 1
    val pwmDriver = Module(new MVPBlackBox)
    pwmDriver.io.in_AUD_PWM := mvp.io.out
    attach(pwmDriver.io.AUD_PWM, io.AUD_PWM)
  }

}

object Nexys4Gen extends App {
  VerilogGen(new Nexys4Wrapper, "MVP_Octave.sv")
}
