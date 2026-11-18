import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class PWMGeneratorSpec extends AnyFlatSpec with ChiselScalatestTester {
  private val width = 4
  private val period = 16

  private def setSamples(dut: PWMGenerator, samples: Int*): Unit = {
    assert(samples.size == dut.io.samples.length)
    for ((sample, index) <- samples.zipWithIndex) {
      dut.io.samples(index).poke(sample.S)
    }
  }

  // Measure the steady-state duty without assuming where the pulse begins.
  private def expectDuty(dut: PWMGenerator, highCycles: Int): Unit = {
    dut.clock.step(period) // allow the new setting to settle
    val actual = (0 until period).count { _ =>
      val high = dut.io.PWM_out.peek().litToBoolean
      dut.clock.step()
      high
    }
    assert(actual == highCycles, s"expected duty $highCycles/$period, got $actual/$period")
  }

  "PWMGenerator" should "increase duty as the signed sample level increases" in {
    test(new PWMGenerator(sampleWid = width)) { dut =>
      dut.io.PWM_en.poke(true.B)

      // Signed zero is the PWM midpoint; minimum and maximum are 0/16 and 15/16.
      val levels = Seq(-8 -> 0, -4 -> 4, 0 -> 8, 4 -> 12, 7 -> 15)
      for ((sample, highCycles) <- levels) {
        setSamples(dut, sample)
        expectDuty(dut, highCycles)
      }
    }
  }

  it should "average simultaneous voices and cancel opposite samples" in {
    test(new PWMGenerator(sampleWid = width, numVoices = 2)) { dut =>
      dut.io.PWM_en.poke(true.B)
      val mixes = Seq(
        (4, 0, 10), // one positive voice plus a silent slot
        (4, 4, 12), // two positive voices, normalized
        (4, -4, 8), // cancellation at the midpoint
        (-4, 4, 8), // cancellation is independent of input order
        (-4, -4, 4)
      )
      for ((first, second, highCycles) <- mixes) {
        setSamples(dut, first, second)
        expectDuty(dut, highCycles)
      }
    }
  }

  it should "preserve signed peaks and normalize all inputs in a three-voice mix" in {
    test(new PWMGenerator(sampleWid = width, numVoices = 3)) { dut =>
      dut.io.PWM_en.poke(true.B)
      val mixes = Seq(
        (7, 7, 7, 15),   // sum 21 must not overflow a signed four-bit sample
        (-8, -8, -8, 0), // sum -24 must not overflow either
        (0, 0, 6, 10),   // the last voice participates: 6 / 3 = 2
        (0, 0, -5, 7)    // signed division truncates -5 / 3 toward zero
      )
      for ((first, second, third, highCycles) <- mixes) {
        setSamples(dut, first, second, third)
        expectDuty(dut, highCycles)
      }
    }
  }

  it should "mute the mixed output when disabled and restore it when enabled" in {
    test(new PWMGenerator(sampleWid = width, numVoices = 2)) { dut =>
      setSamples(dut, 7, 7)
      dut.io.PWM_en.poke(true.B)
      expectDuty(dut, 15)

      dut.io.PWM_en.poke(false.B)
      expectDuty(dut, 0)

      dut.io.PWM_en.poke(true.B)
      expectDuty(dut, 15)
    }
  }
}
