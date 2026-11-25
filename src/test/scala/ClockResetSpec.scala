import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class ClockResetProbe extends Module {
  val io = IO(new Bundle {
    val request = Input(Bool())
    val heldInReset = Output(Bool())
  })
  val synchronizer = Module(new ClockResetSynchronizer)
  synchronizer.clock := clock
  synchronizer.request := io.request
  io.heldInReset := synchronizer.coreReset.asBool
}

class ClockResetSpec extends AnyFlatSpec with ChiselScalatestTester {
  "The board clock reset" should "assert immediately and release only after two core edges" in {
    test(new ClockResetProbe) { dut =>
      dut.io.request.poke(true.B)
      dut.io.heldInReset.expect(true.B)
      dut.clock.step(3)
      dut.io.request.poke(false.B)
      dut.io.heldInReset.expect(true.B)
      dut.clock.step()
      dut.io.heldInReset.expect(true.B)
      dut.clock.step()
      dut.io.heldInReset.expect(false.B)
      // Loss of lock must reassert reset without waiting for another edge.
      dut.io.request.poke(true.B)
      dut.io.heldInReset.expect(true.B)
      dut.io.request.poke(false.B)
      dut.clock.step()
      dut.io.heldInReset.expect(true.B)
      dut.clock.step()
      dut.io.heldInReset.expect(false.B)
    }
  }
}
