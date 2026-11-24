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

// Dedicated clock hardware: 100 MHz * 10 / 40 = 25 MHz, with a 1 GHz VCO.
class Nexys4Clock extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val clk100 = Input(Clock())
    val reset = Input(Bool())
    val clk25 = Output(Clock())
    val locked = Output(Bool())
  })
  setInline("Nexys4Clock.sv", """
module Nexys4Clock(input clk100, input reset, output clk25, output locked);
  wire feedback, feedbackBuffered, outputClock;
  MMCME2_BASE #(
    .CLKIN1_PERIOD(10.0), .DIVCLK_DIVIDE(1),
    .CLKFBOUT_MULT_F(10.0), .CLKOUT0_DIVIDE_F(40.0),
    .STARTUP_WAIT("FALSE")
  ) mmcm (
    .CLKIN1(clk100), .RST(reset), .PWRDWN(1'b0),
    .CLKFBIN(feedbackBuffered), .CLKFBOUT(feedback),
    .CLKOUT0(outputClock), .LOCKED(locked)
  );
  BUFG feedbackBuffer (.I(feedback), .O(feedbackBuffered));
  BUFG outputBuffer (.I(outputClock), .O(clk25));
endmodule
""")
}

// Assert immediately on reset/clock loss; release after two core clocks.
class ClockResetSynchronizer extends RawModule {
  val clock = IO(Input(Clock()))
  val request = IO(Input(Bool()))
  val coreReset = IO(Output(AsyncReset()))
  withClockAndReset(clock, request.asAsyncReset) {
    val first = RegInit(true.B)
    val second = RegInit(true.B)
    first := false.B
    second := first
    coreReset := second.asAsyncReset
  }
}

class MVPIONexys4 extends Bundle {
  val AUD_SD = Output(Bool())
  val AUD_PWM = Analog(1.W) // Linked to AUD_PWM: 0 when low, high-Z when high
  val rx = Input(Bool())
  val heldBin = Output(UInt(7.W)) // connected to leds for debugging
  val seg = Output(UInt(7.W))
  val an = Output(UInt(Nexys4.numAnodes.W))
}

// Handles the Nexys4 board connections
// The board is reset active low. The AUD_PWM is required to be driven correctly.
// This is handled here
// inputs:
//   - io.rx: Bool  // midi input from UART
// outputs:
//  - AUD_PWM: high-Z or 0  // connected to the AUD_PWM port
//  - AUD_SD: Bool  // always high
class Nexys4Wrapper extends RawModule {
  val clock = IO(Input(Clock()))
  val reset = IO(Input(Bool()))

  val clockGenerator = Module(new Nexys4Clock)
  val boardReset = if (Nexys4.resetActiveLow) !reset else reset
  clockGenerator.io.clk100 := clock
  clockGenerator.io.reset := boardReset
  val resetSynchronizer = Module(new ClockResetSynchronizer)
  resetSynchronizer.clock := clockGenerator.io.clk25
  resetSynchronizer.request := boardReset || !clockGenerator.io.locked

  // Keep the synth's registers synchronously reset, including BRAM controls.
  // The synchronizer still asserts the reset request immediately on clock loss.
  withClockAndReset(clockGenerator.io.clk25, resetSynchronizer.coreReset.asBool) {
    val synth = Module(
      new SynthTop(
        clockFrequency = Nexys4.clockFrequency
      )
    )
    val io = IO(new MVPIONexys4())
    io.AUD_SD := true.B // Required by spec

    synth.io.rx := io.rx
    io.heldBin := synth.io.heldBin

    val segDec = Module(new SevenSegDisplay(clockFrequency=Nexys4.clockFrequency, refreshRate=1000, numAnodes=Nexys4.numAnodes))
    segDec.io.value := synth.io.heldBin
    io.seg := segDec.io.seg
    io.an := segDec.io.an
  
    // Set AUD_PWM to be high-Z instead of 1
    val pwmDriver = Module(new MVPBlackBox)
    pwmDriver.io.in_AUD_PWM := synth.io.out
    attach(pwmDriver.io.AUD_PWM, io.AUD_PWM)
  }

}

object Nexys4Gen extends App {
  VerilogGen(new Nexys4Wrapper, "polyphony.sv")
}
