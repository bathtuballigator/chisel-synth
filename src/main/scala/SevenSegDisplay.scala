// Made by claude opus 5.5
import chisel3._
import chisel3.util._

// Shows a 7 bit number (0-127) in base 10 on the 3 rightmost digits of the
// seven segment display. The digits are time multiplexed, each digit is lit
// for `clockFrequency / refreshRate` cycles. Leading zeros are blanked.
// Segments and anodes are active low, like on the Nexys4.
// inputs:
//   - io.value: UInt(7.W)  // number to display
// outputs:
//   - io.seg: UInt(7.W)  // segments, bit 0 = a ... bit 6 = g
//   - io.an: UInt(numAnodes.W)  // digit select, only the lowest 3 are used
class SevenSegDisplay(
    clockFrequency: Int,
    refreshRate: Int = 1000, // Hz per digit
    numAnodes: Int = 8
) extends Module {
  require(numAnodes >= 3)
  val io = IO(new Bundle {
    val value = Input(UInt(7.W))
    val seg = Output(UInt(7.W))
    val an = Output(UInt(numAnodes.W))
  })

  // Lookup table from value to 3 BCD digits (hundreds, tens, ones)
  val bcdTable = VecInit((0 until 128).map { i =>
    ((i / 100) << 8 | (i / 10 % 10) << 4 | i % 10).U(12.W)
  })
  val bcd = bcdTable(io.value)
  val digits = VecInit(bcd(3, 0), bcd(7, 4), bcd(11, 8))

  // Digit 0 is always shown, so 0 is displayed as "0"
  val visible = VecInit(true.B, io.value >= 10.U, io.value >= 100.U)

  // Refresh counter, steps to the next digit when it wraps
  val refreshCycles = math.max(clockFrequency / refreshRate, 1)
  val (_, tick) = Counter(true.B, refreshCycles)
  val (sel, _) = Counter(tick, 3)

  val segOn = SevenSeg.decode(digits(sel))
  io.seg := ~Mux(visible(sel), segOn, 0.U)
  io.an := ~UIntToOH(sel, numAnodes)
}
