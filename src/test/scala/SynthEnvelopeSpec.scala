import chisel3._
import chiseltest._
import chiseltest.experimental.expose
import org.scalatest.flatspec.AnyFlatSpec

class SynthEnvelopeSpec extends AnyFlatSpec with ChiselScalatestTester {
  private val clockHz = 16000
  private val envelope = AdsrConfig(true, 12, 4, BigDecimal("0.5"), 32)

  "An enveloped oscillator" should "ramp signed samples and remain audible during release" in {
    test(new Synth(clockFrequency = clockHz, envelope = envelope)) { dut =>
      dut.io.frequencyHz.poke(250.U) // 64 clocks per square-wave period
      dut.io.gate.poke(false.B)
      dut.io.sample.expect(0.S)
      dut.io.active.expect(false.B)

      def samples(): Seq[Int] = (0 until 64).map { _ =>
        val value = dut.io.sample.peek().litValue.toInt
        dut.clock.step()
        value
      }

      dut.io.gate.poke(true.B)
      val attack = samples()
      assert(attack.exists(_ != 0) && attack.map(math.abs).max < 64)
      dut.clock.step(512)
      val sustain = samples()
      assert(sustain.min == -63 && sustain.max == 63)

      dut.io.gate.poke(false.B)
      val release = samples()
      assert(release.exists(_ != 0))
      dut.io.active.expect(true.B)
      dut.clock.step(640)
      dut.io.active.expect(false.B)
      dut.io.sample.expect(0.S)
    }
  }

  it should "bypass gain for a disabled envelope and silence the oscillator on note-off" in {
    test(new Synth(clockFrequency = clockHz)) { dut =>
      dut.io.frequencyHz.poke(250.U)
      dut.io.gate.poke(true.B)
      dut.io.active.expect(true.B)
      dut.io.sample.expect(127.S)
      dut.io.gate.poke(false.B)
      dut.io.active.expect(false.B)
      dut.io.sample.expect(0.S)
    }
  }

  private class AudioProbe extends SynthAudio(
    Seq(VoiceConfig(Waveform.Square, envelope)), 2, clockHz, sampleWid = 4
  ) {
    val slotNotes = expose(notes)
  }

  "The PWM audio path" should "keep note assignments stable and play the final release tail" in {
    test(new AudioProbe) { dut =>
      def hold(notes: Int*): Unit =
        dut.io.held.poke(notes.foldLeft(BigInt(0))((mask, note) => mask.setBit(note)).U)

      def hasPulses(): Boolean = {
        val samples = (0 until 64).map { _ =>
          val high = dut.io.out.peek().litToBoolean
          dut.clock.step()
          high
        }
        samples.contains(true)
      }

      hold(60, 69)
      dut.clock.step(512)
      dut.slotNotes(0).expect(60.U)
      dut.slotNotes(1).expect(69.U)
      assert(hasPulses())

      hold(69)
      dut.clock.step(32)
      dut.slotNotes(0).expect(60.U)
      dut.slotNotes(1).expect(69.U)
      hold()
      dut.clock.step(32)
      assert(hasPulses(), "PWM muted before the last note completed its release")
      dut.clock.step(640)
      assert(!hasPulses(), "PWM remained active after the release finished")
    }
  }
}
