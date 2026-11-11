import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

/**
  * Sample-level tests, independent of the PWM/audio output stage.
  *
  * Four phase bits give 16 positions per full oscillator period. These literal
  * vectors are the expected samples for amplitude 15, including integer
  * quantization. In particular, the saw must ramp once per FULL phase period,
  * consistent with the tuning-word equation used by OscConfig.
  */
class OscillatorWaveformSpec extends AnyFlatSpec with ChiselScalatestTester {
  private val amplitude = 15
  private val sampleWidth = 8
  private val phaseWidth = 4

  private case class Wave(
      name: String,
      kind: OscillatorType,
      samples: Vector[Int],
      create: (Int, Int, Int) => BaseOscillator
  )

  private val waves = Vector(
    Wave(
      "square", OscillatorType.Square,
      Vector(15, 15, 15, 15, 15, 15, 15, 15,
        -15, -15, -15, -15, -15, -15, -15, -15),
      (amp, sw, pw) => new SquareOscillator(amp, sw, pw)
    ),
    Wave(
      "sine", OscillatorType.Sine,
      Vector(0, 5, 10, 13, 15, 13, 10, 5,
        0, -5, -10, -13, -15, -13, -10, -5),
      (amp, sw, pw) => new SineOscillator(amp, sw, pw)
    ),
    Wave(
      "sawtooth", OscillatorType.Saw,
      Vector(-15, -13, -11, -9, -7, -5, -3, -1,
        1, 3, 5, 7, 9, 11, 13, 15),
      (amp, sw, pw) => new SawOscillator(amp, sw, pw)
    ),
    Wave(
      "triangle", OscillatorType.Triangle,
      Vector(-15, -11, -7, -3, 2, 6, 10, 15,
        15, 10, 6, 2, -3, -7, -11, -15),
      (amp, sw, pw) => new TriangleOscillator(amp, sw, pw)
    )
  )

  for (wave <- waves) {
    behavior of s"${wave.name} oscillator"

    it should "match the expected samples over three complete periods" in {
      test(wave.create(amplitude, sampleWidth, phaseWidth)) { dut =>
        dut.io.increment.poke(1.U)
        for (cycle <- 0 until 3 * wave.samples.length) {
          val phase = cycle % wave.samples.length
          dut.io.sample.expect(wave.samples(phase).S,
            s"${wave.name}: cycle=$cycle, phase=$phase")
          dut.clock.step()
        }
        dut.io.sample.expect(wave.samples.head.S, "phase must wrap back to zero")
      }
    }

    it should "select the correct waveform through the Oscillator wrapper" in {
      test(new Oscillator(wave.kind, amplitude, sampleWidth, phaseWidth,clkFreq=16)) { dut =>
        dut.io.frequencyHz.poke(1.U) // 1 Hz frequency, 16 Hz clock, 4 phase bits = 1 sample per clock
        dut.io.valid.expect(1.B)
        for ((expected, phase) <- wave.samples.zipWithIndex) {
          dut.io.sample.expect(expected.S, s"${wave.name} wrapper: phase=$phase")
          dut.clock.step()
        }
      }
    }

    it should "follow changing increments, hold at zero increment, and wrap modulo 16" in {
      test(wave.create(amplitude, sampleWidth, phaseWidth)) { dut =>
        var phase = 0
        // Includes skipped samples, half-period steps, maximum increments,
        // overflow, and zero increments at nonzero phase.
        val increments = Vector(1, 3, 0, 0, 5, 15, 8, 0, 2, 7, 4, 9, 1, 0, 6, 11)
        for (increment <- increments ++ increments) {
          dut.io.increment.poke(increment.U)
          dut.io.sample.expect(wave.samples(phase).S,
            s"${wave.name}: phase=$phase before increment=$increment")
          dut.clock.step()
          phase = (phase + increment) % wave.samples.length
          dut.io.sample.expect(wave.samples(phase).S,
            s"${wave.name}: phase=$phase after increment=$increment")
        }
      }
    }

    it should "restart at phase zero after a reset during playback" in {
      test(wave.create(amplitude, sampleWidth, phaseWidth)) { dut =>
        dut.io.increment.poke(1.U)
        dut.clock.step(5)
        dut.io.sample.expect(wave.samples(5).S)

        dut.reset.poke(true.B)
        dut.clock.step()
        dut.io.sample.expect(wave.samples.head.S)
        dut.reset.poke(false.B)
        dut.clock.step()
        dut.io.sample.expect(wave.samples(1).S)
      }
    }

    it should "output zero for zero amplitude at every phase" in {
      test(wave.create(0, sampleWidth, phaseWidth)) { dut =>
        dut.io.increment.poke(1.U)
        for (_ <- 0 until 32) {
          dut.io.sample.expect(0.S)
          dut.clock.step()
        }
      }
    }

    it should "preserve the signed peak values with the smallest fitting sample width" in {
      // Amplitude 15 fits in five signed bits (-16 to 15).
      test(wave.create(amplitude, 5, phaseWidth)) { dut =>
        dut.io.increment.poke(1.U)
        for (expected <- wave.samples) {
          dut.io.sample.expect(expected.S)
          dut.clock.step()
        }
      }
    }
  }

  behavior of "sine lookup table"

  it should "use high phase bits when the accumulator is wider than the lookup table" in {
    // Twelve phase bits and a 1024-entry table: phases 0 through 3 share entry 0.
    test(new SineOscillator(127, 8, 12)) { dut =>
      dut.io.increment.poke(1.U)
      dut.clock.step(4 * 128)
      for (phase <- 4 * 128 until 4 * 128 + 8) {
        // Ideal sine with phase truncated to the table's 1024 positions.
        val position = phase / 4
        val ideal = 127 * math.sin(2 * math.Pi * position / 1024)
        val actual = dut.io.sample.peek().litValue.toInt
        assert(math.abs(actual - ideal) < 1.0,
          s"phase=$phase: sample=$actual, ideal=$ideal")
        dut.clock.step()
      }

      // Reach the quarter-period peak, then the negative peak.
      dut.io.increment.poke(0.U)
      dut.reset.poke(true.B)
      dut.clock.step()
      dut.reset.poke(false.B)
      dut.io.increment.poke(1024.U)
      dut.clock.step()
      dut.io.sample.expect(127.S)
      dut.clock.step(2)
      dut.io.sample.expect((-127).S)
    }
  }
}
