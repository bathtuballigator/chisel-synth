import chisel3._
import chiseltest._
import chiseltest.experimental.expose
import org.scalatest.flatspec.AnyFlatSpec

class SynthEnvelopeSpec extends AnyFlatSpec with ChiselScalatestTester {
  private val clockHz = 16000
  private val envelope = AdsrConfig(true, 12, 4, BigDecimal("0.5"), 32)

  "An enveloped oscillator" should "ramp signed samples and remain audible during release" in {
    test(new Synth(clockFrequency = clockHz, envelope = envelope)) { dut =>
      dut.io.increment.poke(((BigInt(1) << 32) / 64).U) // 64 clocks per square-wave period
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
      dut.io.increment.poke(((BigInt(1) << 32) / 64).U)
      dut.io.gate.poke(true.B)
      dut.clock.step(4) // main's registered oscillator input must settle
      dut.io.active.expect(true.B)
      dut.io.sample.expect(127.S)
      dut.io.gate.poke(false.B)
      dut.io.active.expect(false.B)
      dut.io.sample.expect(0.S)
    }
  }

  private class AudioProbe(
      voices: Seq[VoiceConfig] = Seq(VoiceConfig(Waveform.Square, envelope)),
      slots: Int = 2
  ) extends SynthAudio(
    voices, slots, clockHz, sampleWid = 4
  ) {
    val slotNotes = expose(notes)
    val slotAssigned = expose(assigned)
  }

  "The PWM audio path" should "keep note assignments stable and play the final release tail" in {
    test(new AudioProbe()) { dut =>
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

  it should "layer the same held notes across configured waveform banks" in {
    test(new AudioProbe(Seq(
      VoiceConfig(Waveform.Square, envelope),
      VoiceConfig(Waveform.Triangle, envelope),
      VoiceConfig(Waveform.Sawtooth, envelope)
    ))) { dut =>
      dut.io.held.poke((BigInt(0).setBit(60).setBit(69)).U)
      dut.clock.step(64)
      for (index <- 0 until 6) {
        dut.slotAssigned(index).expect(true.B)
        dut.slotNotes(index).expect((if (index % 2 == 0) 60 else 69).U)
      }
    }
  }

  it should "share selection fairly between banks with different release times" in {
    test(new AudioProbe(Seq(
      VoiceConfig(Waveform.Square, envelope),
      VoiceConfig(Waveform.Triangle, envelope.copy(releaseMs = 4))
    ), slots = 1)) { dut =>
      def hold(notes: Int*): Unit =
        dut.io.held.poke(notes.foldLeft(BigInt(0))((mask, note) => mask.setBit(note)).U)

      hold(60)
      dut.clock.step(512)
      for (i <- 0 until 2) dut.slotNotes(i).expect(60.U)
      hold(69)
      dut.clock.step(160)
      dut.slotNotes(0).expect(60.U) // Long release retains its slot.
      dut.slotNotes(1).expect(69.U) // Short release already accepts the waiting note.
      dut.clock.step(640)
      for (i <- 0 until 2) {
        dut.slotAssigned(i).expect(true.B)
        dut.slotNotes(i).expect(69.U)
      }
    }
  }

  it should "fill free slots with higher notes while lower notes remain reserved" in {
    test(new AudioProbe(Seq(
      VoiceConfig(Waveform.Square, envelope),
      VoiceConfig(Waveform.Triangle, envelope)
    ))) { dut =>
      dut.io.held.poke((BigInt(1) << 60).U)
      dut.clock.step(128)
      dut.io.held.poke((BigInt(0).setBit(60).setBit(69)).U)
      dut.clock.step(128)
      for (bank <- 0 until 2) {
        dut.slotNotes(bank * 2).expect(60.U)
        dut.slotNotes(bank * 2 + 1).expect(69.U)
        dut.slotAssigned(bank * 2 + 1).expect(true.B)
      }
    }
  }

  it should "discard a note released while the sequential selector is working" in {
    test(new AudioProbe()) { dut =>
      dut.io.held.poke((BigInt(1) << 60).U)
      dut.clock.step()
      dut.io.held.poke(0.U)
      dut.clock.step(32)
      for (index <- 0 until 2) dut.slotAssigned(index).expect(false.B)
      dut.io.out.expect(false.B)
    }
  }

  it should "wait for release before assigning a waiting note to a full bank" in {
    test(new AudioProbe(slots = 1)) { dut =>
      dut.io.held.poke((BigInt(1) << 60).U)
      dut.clock.step(512)
      dut.io.held.poke((BigInt(0).setBit(60).setBit(69)).U)
      dut.clock.step(32)
      dut.slotNotes(0).expect(60.U)
      dut.io.held.poke((BigInt(1) << 69).U)
      dut.clock.step(32)
      dut.slotNotes(0).expect(60.U)
      dut.clock.step(640)
      dut.slotAssigned(0).expect(true.B)
      dut.slotNotes(0).expect(69.U)
    }
  }
}
