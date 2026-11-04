import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

// End-to-end: UART bytes on rx at the real clock and baud -> heldBin
class SynthTopSpec extends AnyFlatSpec with ChiselScalatestTester {
  val div = Nexys4.clockFrequency / Midi.midiBaudRate

  def sendByte(dut: SynthTop, b: Int): Unit = {
    val bits = Seq(0) ++ (0 until 8).map(i => (b >> i) & 1) ++ Seq(1) // 8N1
    for (bit <- bits) {
      dut.io.rx.poke((bit == 1).B)
      dut.clock.step(div)
    }
  }

  "SynthTop" should "show the held note on heldBin" in test(new SynthTop()) { dut =>
    dut.clock.setTimeout(0)
    dut.io.rx.poke(true.B)
    dut.clock.step(10)
    dut.io.heldBin.expect(0.U)

    Seq(0x90, 60, 100).foreach(sendByte(dut, _)) // note on C4
    dut.clock.step(10)
    dut.io.heldBin.expect(60.U)

    Seq(0x80, 0, 0).foreach(sendByte(dut, _)) // note off
    dut.clock.step(10)
    dut.io.heldBin.expect(0.U)
  }
}
