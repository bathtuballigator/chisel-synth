import _root_.circt.stage.ChiselStage
import chisel3._
import chisel3.util._

/** 8N1 UART receiver. Samples each bit in the middle of its period. */
class UartRx(clkHz: Int, baud: Int) extends Module {
  val io = IO(new Bundle {
    val rx = Input(Bool())
    val out = Valid(UInt(8.W))
  })

  val div = clkHz / baud
  require(div >= 4, "clock too slow for this baud rate")

  // 2-FF synchronizer, idle high
  val r = RegNext(RegNext(io.rx, true.B), true.B)

  val cnt = RegInit(0.U(log2Ceil(div).W))
  val bitn = RegInit(0.U(4.W))
  val busy = RegInit(false.B)
  val sh = Reg(UInt(8.W))
  val data = Reg(UInt(8.W))
  val valid = RegInit(false.B)

  valid := false.B

  when(!busy) {
    when(!r) { // falling edge = start bit
      busy := true.B
      cnt := (div / 2).U
      bitn := 0.U
    }
  }.elsewhen(cnt === 0.U) {
    cnt := (div - 1).U
    bitn := bitn + 1.U
    when(bitn === 0.U) {
      when(r) { busy := false.B } // false start (glitch)
    }.elsewhen(bitn <= 8.U) {
      sh := Cat(r, sh(7, 1)) // LSB first
    }.otherwise { // stop bit
      busy := false.B
      when(r) {
        data := sh
        valid := true.B
      }
    }
  }.otherwise {
    cnt := cnt - 1.U
  }

  io.out.valid := valid
  io.out.bits := data
}

class MidiMsg extends Bundle {
  val status = UInt(8.W) // e.g. 0x90 | channel
  val data1 = UInt(7.W) // note / controller number
  val data2 = UInt(7.W) // velocity / value (0 for 1-data-byte messages)
}

/** Turns a MIDI byte stream into complete channel messages. Handles running
  * status, ignores real-time bytes (0xF8-0xFF) wherever they appear, and drops
  * SysEx and system common messages.
  */
class MidiParser extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Valid(UInt(8.W)))
    val msg = Valid(new MidiMsg)
  })

  val status = RegInit(0.U(8.W)) // 0 = no valid running status
  val d1 = Reg(UInt(7.W))
  val haveD1 = RegInit(false.B)
  val outValid = RegInit(false.B)
  val outMsg = Reg(new MidiMsg)

  outValid := false.B

  // Program change (0xC) and channel pressure (0xD) have one data byte
  def twoDataBytes(s: UInt): Bool = {
    val hi = s(7, 4)
    hi =/= 0xc.U && hi =/= 0xd.U
  }

  val b = io.in.bits

  when(io.in.valid) {
    when(b >= 0xf8.U) {
      // real-time (clock, start, stop...): ignore, keep state
    }.elsewhen(b(7)) {
      haveD1 := false.B
      // Channel status sets running status; any system byte (F0-F7) clears it,
      // so SysEx and system-common data bytes are discarded
      status := Mux(b < 0xf0.U, b, 0.U)
    }.elsewhen(status =/= 0.U) {
      when(!twoDataBytes(status)) {
        outMsg.status := status
        outMsg.data1 := b(6, 0)
        outMsg.data2 := 0.U
        outValid := true.B
      }.elsewhen(!haveD1) {
        d1 := b(6, 0)
        haveD1 := true.B
      }.otherwise {
        outMsg.status := status
        outMsg.data1 := d1
        outMsg.data2 := b(6, 0)
        outValid := true.B
        haveD1 := false.B // ready for next running-status message
      }
    }
  }

  io.msg.valid := outValid
  io.msg.bits := outMsg
}

/** Top level: UART pin in, decoded events and a held-notes bitmap out. */
class MidiTop(clkHz: Int = Nexys4.clockFrequency, baud: Int = Env.midiBaudRate)
    extends Module {
  val io = IO(new Bundle {
    val rx = Input(Bool())
    val noteOn = Output(Bool()) // 1-cycle pulse
    val noteOff = Output(Bool()) // 1-cycle pulse
    val cc = Output(Bool()) // 1-cycle pulse (knobs, some pads)
    val bend = Output(Bool()) // 1-cycle pulse (pitch bend)
    val channel = Output(UInt(4.W))
    val data1 = Output(UInt(7.W)) // note or CC number
    val data2 = Output(UInt(7.W)) // velocity or CC value
    val held =
      Output(UInt(128.W)) // bit n = note n currently held (any channel)
  })

  val uart = Module(new UartRx(clkHz, baud))
  val parser = Module(new MidiParser)

  uart.io.rx := io.rx
  parser.io.in := uart.io.out

  val m = parser.io.msg
  val kind = m.bits.status(7, 4)

  // Note-on with velocity 0 means note-off
  val isOn = m.valid && kind === 0x9.U && m.bits.data2 =/= 0.U
  val isOff =
    m.valid && (kind === 0x8.U || (kind === 0x9.U && m.bits.data2 === 0.U))

  io.noteOn := isOn
  io.noteOff := isOff
  io.cc := m.valid && kind === 0xb.U
  io.bend := m.valid && kind === 0xe.U
  io.channel := m.bits.status(3, 0)
  io.data1 := m.bits.data1
  io.data2 := m.bits.data2

  val held = RegInit(VecInit(Seq.fill(128)(false.B)))
  when(isOn) { held(m.bits.data1) := true.B }
  when(isOff) { held(m.bits.data1) := false.B }
  io.held := held.asUInt
}

object Main extends App {
  VerilogGen(
    new MidiTop(clkHz = Nexys4.clockFrequency, baud = Env.midiBaudRate),
    "midiTop.sv"
  )
}
