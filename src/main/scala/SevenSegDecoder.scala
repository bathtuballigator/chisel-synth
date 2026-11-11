// From Chisel lab5 in digital electronics 2 https://github.com/lassenorbjerg/chisel-lab
import chisel3._
import chisel3.util._

// Combinational hex digit to segment pattern, active high, bit 0 = a ... bit 6 = g
object SevenSeg {
  def decode(digit: UInt): UInt = {
    val sevSeg = WireDefault(0.U(7.W))

    switch (digit) {
      is("h0".U){sevSeg:=(0x4+0x8+0x10+0x20+0x01+0x02).U}
      is("h1".U){sevSeg:=(0x4+0x2).U}
      is("h2".U){sevSeg:=(0x1+0x2+0x40+0x10+0x8).U}
      is("h3".U){sevSeg:=(0x1+0x2+0x40+0x4+0x8).U}
      is("h4".U){sevSeg:=(0x20+0x2+0x40+0x4).U}
      is("h5".U){sevSeg:=(0x1+0x20+0x40+0x4+0x8).U}
      is("h6".U){sevSeg:=(0x1+0x20+0x40+0x10+0x8+0x4).U}
      is("h7".U){sevSeg:=(0x4+0x2+0x1).U}
      is("h8".U){sevSeg:=("b1111111").U}
      is("h9".U){sevSeg:=(0x01+0x20+0x2+0x40+0x8+0x4).U}
      is("ha".U){sevSeg:=("b1110111").U}
      is("hb".U){sevSeg:=("b1111100").U}
      is("hc".U){sevSeg:=("b0111001").U}
      is("hd".U){sevSeg:=("b1011110").U}
      is("he".U){sevSeg:=("b1111001").U}
      is("hf".U){sevSeg:=("b1110001").U}
    }
    sevSeg
  }
}

class SevenSegDecoder extends Module {
  val io = IO(new Bundle {
    val sw = Input(UInt(4.W))
    val seg = Output(UInt(7.W))
    val an = Output(UInt(4.W))
  })

  val sevSeg = SevenSeg.decode(io.sw)

  io.seg := ~sevSeg
  io.an := "b1110".U
}

