import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class ADSREnvelopeSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "ADSREnvelope"

  def newEnv = new ADSREnvelope(
    attackMs = 4, decayMs = 10, sustain = 0.5, releaseMs = 5,
    clockHz = 1000, levelWid = 8, tickHz = 1000
  )

  it should "stay silent until the gate goes high" in {
    test(newEnv) { dut =>
      dut.io.gate.poke(false.B)
      dut.clock.step(10)
      dut.io.level.expect(0.U)
      dut.io.active.expect(false.B)
    }
  }

  it should "attack, decay, sustain and release" in {
    test(newEnv) { dut =>
      dut.io.gate.poke(true.B)
      var peak = 0
      for (_ <- 0 until 30) {
        dut.clock.step()
        peak = peak max dut.io.level.peek().litValue.toInt
      }
      assert(peak == 255)
      dut.io.level.expect(128.U)
      dut.io.active.expect(true.B)

      dut.clock.step(20)
      dut.io.level.expect(128.U)

      dut.io.gate.poke(false.B)
      dut.clock.step(10)
      dut.io.level.expect(0.U)
      dut.io.active.expect(false.B)
    }
  }

  it should "release immediately if the key is let go during attack" in {
    test(newEnv) { dut =>
      dut.io.gate.poke(true.B)
      dut.clock.step(2)
      dut.io.gate.poke(false.B)
      dut.clock.step(10)
      dut.io.level.expect(0.U)
      dut.io.active.expect(false.B)
    }
  }
}