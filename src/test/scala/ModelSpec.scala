import chisel3._
import chiseltest._
import chiseltest.simulator.WriteVcdAnnotation
import org.scalatest.flatspec.AnyFlatSpec
import chisel3.simulator.PeekPokeAPI
import OscillatorType.Square
import scala.sys.process._

class ModelSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Golden Model"
  val sampleWid = 8
  val phaseWid = 32
  it should "pass fft testing in default settings" in {
    val freq = MidiUtils.midiToFreq(Env.rootMidiNote)
    val increment = MidiUtils.midiIncrement(
      MidiUtils.midiToFreq(Env.rootMidiNote),
      phaseWid,
      Nexys4.clockFrequency
    )
    test(new Oscillator(OscillatorType.Square, 127, sampleWid, phaseWid)) {
      dut =>
        val simSteps =
          (((Nexys4.clockFrequency / freq).toFloat).floor * 25).toInt
        dut.io.increment.poke(increment)
        dut.clock.setTimeout(0)
        val output: IndexedSeq[Int] = (0 until simSteps).map(_ => {
          dut.clock.step(1)
          dut.io.sample.peek().litValue.toInt
        })
        val path: os.Path = os.pwd / "src" / "test" / "resources" / "data.log"

        os.write.over(path, output.mkString(","))
        val exit_code =
          s"./.venv/bin/python src/test/resources/fft.py -c ${Nexys4.clockFrequency} -f ${freq} -s square".!
        assert(exit_code == 0)
    }
  }
  it should "pass as sine wave" in {
    val freq = MidiUtils.midiToFreq(Env.rootMidiNote)
    val increment = MidiUtils.midiIncrement(
      MidiUtils.midiToFreq(Env.rootMidiNote),
      phaseWid,
      Nexys4.clockFrequency
    )

    test(new Oscillator(OscillatorType.Sine, 127, sampleWid, phaseWid)) { dut =>
      val simSteps =
        (((Nexys4.clockFrequency / freq).toFloat).floor * 15).toInt
      dut.io.increment.poke(increment)
      dut.clock.setTimeout(0)
      val output: IndexedSeq[Int] = (0 until simSteps).map(i => {
        dut.clock.step(1)
        dut.io.sample.peek().litValue.toInt
      })
      val path: os.Path = os.pwd / "src" / "test" / "resources" / "data.log"

      os.write.over(path, output.mkString(","))
      val exit_code =
        s"./.venv/bin/python src/test/resources/fft.py -c ${Nexys4.clockFrequency} -f ${freq} -s sine".!
      assert(exit_code == 0)
    }
  }
  it should "pass as triangle wave" in {
    val freq = MidiUtils.midiToFreq(Env.rootMidiNote)
    val increment = MidiUtils.midiIncrement(
      MidiUtils.midiToFreq(Env.rootMidiNote),
      phaseWid,
      Nexys4.clockFrequency
    )
    test(new Oscillator(OscillatorType.Triangle, 127, sampleWid, phaseWid)) {
      dut =>
        val simSteps =
          (((Nexys4.clockFrequency / freq).toFloat).floor * 25).toInt
        dut.io.increment.poke(increment)
        dut.clock.setTimeout(0)
        val output: IndexedSeq[Int] = (0 until simSteps).map(_ => {
          dut.clock.step(1)
          dut.io.sample.peek().litValue.toInt
        })
        val path: os.Path = os.pwd / "src" / "test" / "resources" / "data.log"

        os.write.over(path, output.mkString(","))
        val exit_code =
          s"./.venv/bin/python src/test/resources/fft.py -c ${Nexys4.clockFrequency} -f ${freq} -s triangle".!
        assert(exit_code == 0)
    }
  }
  it should "pass as sawtooth wave" in {
    val freq = MidiUtils.midiToFreq(Env.rootMidiNote)
    val increment = MidiUtils.midiIncrement(
      MidiUtils.midiToFreq(Env.rootMidiNote),
      phaseWid,
      Nexys4.clockFrequency
    )
    test(new Oscillator(OscillatorType.Saw, 127, sampleWid, phaseWid)) { dut =>
      val simSteps =
        (((Nexys4.clockFrequency / freq).toFloat).floor * 25).toInt
      dut.io.increment.poke(increment)
      dut.clock.setTimeout(0)
      val output: IndexedSeq[Int] = (0 until simSteps).map(_ => {
        dut.clock.step(1)
        dut.io.sample.peek().litValue.toInt
      })
      val path: os.Path = os.pwd / "src" / "test" / "resources" / "data.log"

      os.write.over(path, output.mkString(","))
      val exit_code =
        s"./.venv/bin/python src/test/resources/fft.py -c ${Nexys4.clockFrequency} -f ${freq} -s sawtooth".!
      assert(exit_code == 0)
    }
  }

}

class ModelSpecLong extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Golden Model"
  val sampleWid = 8
  val phaseWid = 32
  it should "pass fft testing in default settings" in {
    val freq = MidiUtils.midiToFreq(Env.rootMidiNote)
    val increment = MidiUtils.midiIncrement(
      MidiUtils.midiToFreq(Env.rootMidiNote),
      phaseWid,
      Nexys4.clockFrequency
    )
    test(new Oscillator(OscillatorType.Square, 127, sampleWid, phaseWid)) {
      dut =>
        val simSteps =
          (((Nexys4.clockFrequency / freq).toFloat).floor * 25).toInt
        dut.io.increment.poke(increment)
        dut.clock.setTimeout(0)
        val output: IndexedSeq[Int] = (0 until simSteps).map(_ => {
          dut.clock.step(1)
          dut.io.sample.peek().litValue.toInt
        })
        val path: os.Path = os.pwd / "src" / "test" / "resources" / "data.log"

        os.write.over(path, output.mkString(","))
        val exit_code =
          s"./.venv/bin/python src/test/resources/fft.py -c ${Nexys4.clockFrequency} -f ${freq} -s square".!
        assert(exit_code == 0)
    }
  }
  it should "pass as square wave with multiple freqs" in {
    for (i <- (0 until 24)) {
      val freq = MidiUtils.midiToFreq(Env.rootMidiNote + i)
      val increment = MidiUtils.midiIncrement(
        MidiUtils.midiToFreq(Env.rootMidiNote + i),
        phaseWid,
        Nexys4.clockFrequency
      )
      test(new Oscillator(OscillatorType.Square, 127, sampleWid, phaseWid)) {
        dut =>
          val simSteps =
            (((Nexys4.clockFrequency / freq).toFloat).floor * 25).toInt
          dut.io.increment.poke(increment)
          dut.clock.setTimeout(0)
          val output: IndexedSeq[Int] = (0 until simSteps).map(_ => {
            dut.clock.step(1)
            dut.io.sample.peek().litValue.toInt
          })
          val path: os.Path = os.pwd / "src" / "test" / "resources" / "data.log"

          os.write.over(path, output.mkString(","))
          val exit_code =
            s"./.venv/bin/python src/test/resources/fft.py -c ${Nexys4.clockFrequency} -f ${freq} -s square".!
          assert(exit_code == 0)
      }

    }
  }
  it should "pass as sine wave with multiple freqs" in {
    for (i <- (0 until 24)) {
      val freq = MidiUtils.midiToFreq(Env.rootMidiNote)
      val increment = MidiUtils.midiIncrement(
        MidiUtils.midiToFreq(Env.rootMidiNote),
        phaseWid,
        Nexys4.clockFrequency
      )

      test(new Oscillator(OscillatorType.Sine, 127, sampleWid, phaseWid)) {
        dut =>
          val simSteps =
            (((Nexys4.clockFrequency / freq).toFloat).floor * 15).toInt
          dut.io.increment.poke(increment)
          dut.clock.setTimeout(0)
          val output: IndexedSeq[Int] = (0 until simSteps).map(i => {
            dut.clock.step(1)
            dut.io.sample.peek().litValue.toInt
          })
          val path: os.Path = os.pwd / "src" / "test" / "resources" / "data.log"

          os.write.over(path, output.mkString(","))
          val exit_code =
            s"./.venv/bin/python src/test/resources/fft.py -c ${Nexys4.clockFrequency} -f ${freq} -s sine".!
          assert(exit_code == 0)
      }

    }
  }
  it should "pass as triangle wave with multiple freqs" in {
    for (i <- (0 until 24)) {
      val freq = MidiUtils.midiToFreq(Env.rootMidiNote)
      val increment = MidiUtils.midiIncrement(
        MidiUtils.midiToFreq(Env.rootMidiNote),
        phaseWid,
        Nexys4.clockFrequency
      )
      test(new Oscillator(OscillatorType.Triangle, 127, sampleWid, phaseWid)) {
        dut =>
          val simSteps =
            (((Nexys4.clockFrequency / freq).toFloat).floor * 25).toInt
          dut.io.increment.poke(increment)
          dut.clock.setTimeout(0)
          val output: IndexedSeq[Int] = (0 until simSteps).map(_ => {
            dut.clock.step(1)
            dut.io.sample.peek().litValue.toInt
          })
          val path: os.Path = os.pwd / "src" / "test" / "resources" / "data.log"

          os.write.over(path, output.mkString(","))
          val exit_code =
            s"./.venv/bin/python src/test/resources/fft.py -c ${Nexys4.clockFrequency} -f ${freq} -s triangle".!
          assert(exit_code == 0)
      }

    }
  }
  it should "pass as sawtooth wave with multiple freqs" in {
    for (i <- (0 until 24)) {
      val freq = MidiUtils.midiToFreq(Env.rootMidiNote)
      val increment = MidiUtils.midiIncrement(
        MidiUtils.midiToFreq(Env.rootMidiNote),
        phaseWid,
        Nexys4.clockFrequency
      )
      test(new Oscillator(OscillatorType.Saw, 127, sampleWid, phaseWid)) {
        dut =>
          val simSteps =
            (((Nexys4.clockFrequency / freq).toFloat).floor * 25).toInt
          dut.io.increment.poke(increment)
          dut.clock.setTimeout(0)
          val output: IndexedSeq[Int] = (0 until simSteps).map(_ => {
            dut.clock.step(1)
            dut.io.sample.peek().litValue.toInt
          })
          val path: os.Path = os.pwd / "src" / "test" / "resources" / "data.log"

          os.write.over(path, output.mkString(","))
          val exit_code =
            s"./.venv/bin/python src/test/resources/fft.py -c ${Nexys4.clockFrequency} -f ${freq} -s sawtooth".!
          assert(exit_code == 0)
      }
    }

  }
}
