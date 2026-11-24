import chisel3._
import chiseltest._
import chiseltest.simulator.WriteVcdAnnotation
import org.scalatest.flatspec.AnyFlatSpec
import chisel3.simulator.PeekPokeAPI
import OscillatorType.Square
import scala.sys.process._

class ModelSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Golden Model"
  // it should "work right" in {
  //   test(
  //     new MVP(
  //       frequency = MidiUtils.midiToFreq(Env.rootMidiNote),
  //       clockFrequency = Nexys4.clockFrequency
  //     )
  //   ) { dut =>
  //     val simSteps = 10_000_000
  //     val model = new GoldenModel(Nexys4.clockFrequency, simSteps, 0)
  //     dut.io.play.poke(true.B)
  //     dut.clock.setTimeout(0)
  //     for (i <- 0 until simSteps) {
  //       dut.clock.step()
  //       dut.io.out.expect(model.step(0))
  //     }
  //   }
  // }
  // it should "work with non-mvp oscillator" in {
  //   test(new PWMGenerator(freq = MidiUtils.midiToFreq(Env.rootMidiNote))) {
  //     dut =>
  //       val simSteps = 10_000_000
  //       val model = new GoldenModel(Nexys4.clockFrequency, simSteps, 0)
  //       dut.io.PWM_en.poke(true.B)
  //       dut.clock.setTimeout(0)
  //       for (i <- 0 until simSteps) {
  //         println(
  //           s"at step $i, dut is: ${dut.io.PWM_out.peek()}, model is: ${model.peek()}"
  //         )
  //         println(s"current sample value: ${dut.io.sample.peek()}")
  //         dut.clock.step()
  //         dut.io.PWM_out.expect(model.step(0))
  //       }
  //   }
  // }
  it should "pass fft testing in default settings" in {
    val sampleWid = 8
    val freq = MidiUtils.midiToFreq(Env.rootMidiNote)
    val phaseWid = OscConfig.calcPhaseWid(
      freq,
      Nexys4.clockFrequency
    )
    val increment =
      OscConfig.calcIncrement(freq, Nexys4.clockFrequency, phaseWid)
    test(new Oscillator(OscillatorType.Square, 127, sampleWid, phaseWid)) {
      dut =>
        val simSteps =
          (((Nexys4.clockFrequency / freq).toFloat).floor * 30).toInt
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
