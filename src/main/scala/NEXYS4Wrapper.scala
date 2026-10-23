import chisel3._
import chisel3.util._

// 2-FF synchronizer + level debouncer. Output changes only after the
// synchronized input has held its new value for `stableCycles` cycles.
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

class NEXYS4Wrapper(
    resetActiveLow: Boolean =
      true // true means reset is active when not asserting it
) extends RawModule {
  val clock = IO(Input(Clock()))
  val reset = IO(Input(Bool()))

  withClockAndReset(clock, if (resetActiveLow) !reset else reset) {
    val mvp = Module(
      new MVP(frequency = 440, clockFrequency = 100_000_000, useZ = true)
    )
    val io = IO(chiselTypeOf(mvp.io))

      io <> mvp.io

    // 10 ms at 100MHz is more than plenty
    val debounceCycles = 1_000_000
    val play = Module(new Debouncer(debounceCycles))

    play.io.in := io.play
  }

}
