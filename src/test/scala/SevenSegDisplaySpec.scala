import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class SevenSegDisplaySpec extends AnyFlatSpec with ChiselScalatestTester {
  // active low segment patterns
  def pat(d: Int): Int = Seq(0x3f, 0x06, 0x5b, 0x4f, 0x66, 0x6d, 0x7d, 0x07, 0x7f, 0x6f)(d) ^ 0x7f
  val blank = 0x7f

  // refreshRate == clockFrequency so the digit changes every cycle
  def dut = new SevenSegDisplay(clockFrequency = 1, refreshRate = 1)

  def expectDigits(d: SevenSegDisplay, value: Int, segs: Seq[Int]): Unit = {
    d.io.value.poke(value.U)
    for ((s, i) <- segs.zipWithIndex) {
      d.io.an.expect((~(1 << i) & 0xff).U)
      d.io.seg.expect(s.U)
      d.clock.step()
    }
  }

  "SevenSegDisplay" should "show all three digits of 127" in test(dut) { d =>
    expectDigits(d, 127, Seq(pat(7), pat(2), pat(1)))
  }

  it should "blank leading zeros" in test(dut) { d =>
    expectDigits(d, 5, Seq(pat(5), blank, blank))
    expectDigits(d, 42, Seq(pat(2), pat(4), blank))
    expectDigits(d, 0, Seq(pat(0), blank, blank))
  }

  it should "show zeros that are not leading" in test(dut) { d =>
    expectDigits(d, 100, Seq(pat(0), pat(0), pat(1)))
  }
}
