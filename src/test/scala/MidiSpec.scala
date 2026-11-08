import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.mutable.ArrayBuffer
import scala.sys.process._
import scala.util.Try

/** EphemeralSimulator needs Verilator; tests are cancelled (skipped) without it. */
object Verilator {
  lazy val available: Boolean =
    Try(Seq("verilator", "--version").!(ProcessLogger(_ => ())) == 0).getOrElse(false)
}

/** End-to-end tests: serialized bits on the rx pin -> decoded events. */
class MidiTopSpec extends AnyFlatSpec with Matchers {
  // Real MIDI baud, but a low clock so each bit is only 16 cycles (fast sim)
  val ClkHz = 500000
  val Baud  = 31250
  val Div   = ClkHz / Baud

  case class Ev(kind: String, ch: Int, d1: Int, d2: Int)

  class Driver(dut: MidiTop) {
    val events = ArrayBuffer[Ev]()

    /** Drive rx for one clock cycle and record any output pulses. */
    def tick(rx: Boolean): Unit = {
      dut.io.rx.poke(rx.B)
      val ch = dut.io.channel.peek().litValue.toInt
      val d1 = dut.io.data1.peek().litValue.toInt
      val d2 = dut.io.data2.peek().litValue.toInt
      if (dut.io.noteOn.peek().litToBoolean)  events += Ev("on", ch, d1, d2)
      if (dut.io.noteOff.peek().litToBoolean) events += Ev("off", ch, d1, d2)
      if (dut.io.cc.peek().litToBoolean)      events += Ev("cc", ch, d1, d2)
      if (dut.io.bend.peek().litToBoolean)    events += Ev("bend", ch, d1, d2)
      dut.clock.step()
    }

    def idle(n: Int): Unit = (0 until n).foreach(_ => tick(true))

    /** One 8N1 frame: start bit, 8 data bits LSB first, stop bit. */
    def sendByte(b: Int): Unit = {
      (0 until Div).foreach(_ => tick(false))
      for (i <- 0 until 8; _ <- 0 until Div) tick(((b >> i) & 1) == 1)
      (0 until Div).foreach(_ => tick(true))
    }

    def send(bytes: Int*): Unit = {
      bytes.foreach(sendByte)
      idle(2 * Div)
    }

    def held: BigInt = dut.io.held.peek().litValue
  }

  def run(body: Driver => Unit): Unit = {
    assume(Verilator.available, "verilator not found on PATH")
    simulate(new MidiTop(ClkHz, Baud)) { dut =>
      dut.io.rx.poke(true.B)
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)
      val d = new Driver(dut)
      d.idle(Div)
      body(d)
    }
  }

  "MidiTop" should "decode a note on and track it as held" in run { d =>
    d.send(0x90, 0x3C, 0x64) // ch 1, middle C, velocity 100
    d.events.toSeq shouldBe Seq(Ev("on", 0, 60, 100))
    d.held.testBit(60) shouldBe true
  }

  it should "treat note on with velocity 0 as note off" in run { d =>
    d.send(0x90, 0x3C, 0x64)
    d.send(0x90, 0x3C, 0x00)
    d.events.toSeq shouldBe Seq(Ev("on", 0, 60, 100), Ev("off", 0, 60, 0))
    d.held shouldBe BigInt(0)
  }

  it should "handle running status" in run { d =>
    d.send(0x93, 0x3C, 0x50, 0x40, 0x60, 0x3C, 0x00) // ch 4: on C, on E, off C
    d.events.toSeq shouldBe Seq(
      Ev("on", 3, 60, 80),
      Ev("on", 3, 64, 96),
      Ev("off", 3, 60, 0)
    )
    d.held.testBit(64) shouldBe true
    d.held.testBit(60) shouldBe false
  }

  it should "ignore real-time bytes in the middle of a message" in run { d =>
    d.send(0x90, 0xF8, 0x3C, 0xF8, 0x64) // MIDI clock interleaved
    d.events.toSeq shouldBe Seq(Ev("on", 0, 60, 100))
  }

  it should "decode control change and pitch bend" in run { d =>
    d.send(0xB0, 0x46, 0x7F) // knob CC 70 -> 127
    d.send(0xE0, 0x00, 0x40) // pitch bend centre
    d.events.toSeq shouldBe Seq(Ev("cc", 0, 70, 127), Ev("bend", 0, 0, 64))
  }

  it should "drop SysEx and not reuse running status after it" in run { d =>
    d.send(0x90, 0x3C, 0x64)
    d.send(0xF0, 0x47, 0x12, 0x34, 0xF7) // Akai-style SysEx
    d.send(0x3C, 0x00)                   // stray data: must be ignored
    d.send(0x80, 0x3C, 0x00)
    d.events.toSeq shouldBe Seq(Ev("on", 0, 60, 100), Ev("off", 0, 60, 0))
  }

  it should "reject a short glitch on the line" in run { d =>
    d.tick(false); d.tick(false) // 2-cycle low pulse, much shorter than half a bit
    d.idle(3 * Div)
    d.send(0x90, 0x3C, 0x64)
    d.events.toSeq shouldBe Seq(Ev("on", 0, 60, 100))
  }
}

/** Parser-level tests, driving bytes directly (no UART). */
class MidiParserSpec extends AnyFlatSpec with Matchers {
  def feed(bytes: Int*): Seq[(Int, Int, Int)] = {
    assume(Verilator.available, "verilator not found on PATH")
    val out = ArrayBuffer[(Int, Int, Int)]()
    simulate(new MidiParser) { dut =>
      dut.io.in.valid.poke(false.B)
      dut.io.in.bits.poke(0.U)
      dut.reset.poke(true.B)
      dut.clock.step()
      dut.reset.poke(false.B)

      def tick(): Unit = {
        if (dut.io.msg.valid.peek().litToBoolean)
          out += ((
            dut.io.msg.bits.status.peek().litValue.toInt,
            dut.io.msg.bits.data1.peek().litValue.toInt,
            dut.io.msg.bits.data2.peek().litValue.toInt
          ))
        dut.clock.step()
      }

      for (b <- bytes) {
        dut.io.in.valid.poke(true.B)
        dut.io.in.bits.poke(b.U)
        tick()
        dut.io.in.valid.poke(false.B)
        tick(); tick()
      }
    }
    out.toSeq
  }

  "MidiParser" should "handle one-data-byte messages with running status" in {
    feed(0xC5, 0x07, 0x09) shouldBe Seq((0xC5, 7, 0), (0xC5, 9, 0))
  }

  it should "discard data after a system common message" in {
    feed(0x90, 0x3C, 0x64, 0xF2, 0x10, 0x20, 0x3C, 0x00) shouldBe Seq((0x90, 60, 100))
  }

  it should "ignore data bytes before any status byte" in {
    feed(0x3C, 0x64, 0x90, 0x3C, 0x64) shouldBe Seq((0x90, 60, 100))
  }
}
