import chisel3._
import chisel3.util._

class UartIO extends DecoupledIO(UInt(8.W))
/**
  * The following code is inspired by Schoeberl code at:
  *  https://github.com/schoeberl/chisel-book/blob/master/src/main/scala/uart/uart.scala
  * With following doc:
  * Receive part of the UART.
  * A minimal version without any additional buffering.
  * Use a ready/valid handshaking.
  *
  * The following code is inspired by Tommy's receive code at:
  * https://github.com/tommythorn/yarvi
  */
class UartRx(frequency: Int, baudRate: Int) extends Module {
  val io = IO(new Bundle {
    val rxd = Input(UInt(1.W))
    val channel = new UartIO()
  })

  val BIT_CNT = ((frequency + baudRate / 2) / baudRate - 1)
  val START_CNT = ((3 * frequency / 2 + baudRate / 2) / baudRate - 2) // -2 for the falling delay

  // Sync in the asynchronous RX data
  val rxReg = RegNext(RegNext(io.rxd, 0.U), 0.U)
  val falling = !rxReg && (RegNext(rxReg) === 1.U)

  val shiftReg = RegInit(0.U(8.W))
  val cntReg = RegInit(BIT_CNT.U(20.W)) // have some idle time before listening
  val bitsReg = RegInit(0.U(4.W))
  val valReg = RegInit(false.B)
  val checkingStart = RegInit(false.B)

  when(cntReg =/= 0.U) {
    cntReg := cntReg - 1.U
  }.elsewhen(checkingStart) {
    // Confirm the start bit near its centre before receiving any data.
    // A short low glitch must not consume the next complete UART frame.
    checkingStart := false.B
    when(rxReg === 0.U) {
      cntReg := BIT_CNT.U
      bitsReg := 8.U
    }
  }.elsewhen(bitsReg =/= 0.U) {
    cntReg := BIT_CNT.U
    shiftReg := Cat(rxReg, shiftReg >> 1)
    bitsReg := bitsReg - 1.U
    // the last shifted in
    when(bitsReg === 1.U) {
      valReg := true.B
    }
  }.elsewhen(falling) { // wait 1.5 bits after falling edge of start
    if (START_CNT >= BIT_CNT + 1) {
      // Split the original start delay without changing data sampling times.
      cntReg := (START_CNT - BIT_CNT - 1).U
      checkingStart := true.B
    } else {
      // Preserve the two-clocks-per-bit mode used by accelerated top tests.
      cntReg := START_CNT.U
      bitsReg := 8.U
    }
  }

  when(valReg && io.channel.ready) {
    valReg := false.B
  }

  io.channel.bits := shiftReg
  io.channel.valid := valReg
}

class MidiMsg extends Bundle {
  val status = UInt(8.W) // e.g. 0x90 | channel
  val data1 = UInt(7.W) // note / controller number
  val data2 = UInt(7.W) // velocity / value (0 for 1-data-byte messages)
}

/** Turns a MIDI byte stream into complete channel messages. Handles running
  * status, ignores real-time bytes (0xF8-0xFF) wherever they appear, and drops
  * SysEx and system common messages.
  * 
  * in.ready is always true.
  */
class MidiParser extends Module {
  val io = IO(new Bundle {
    val in = Flipped(new UartIO())
    val msg = DecoupledIO(new MidiMsg)
  })
  io.in.ready := true.B

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

class MidiIO extends Bundle {
  val rx = Input(Bool())
  val noteOn = Output(Bool()) // 1-cycle pulse
  val noteOff = Output(Bool()) // 1-cycle pulse
  val cc = Output(Bool()) // 1-cycle pulse (knobs, some pads)
  val bend = Output(Bool()) // 1-cycle pulse (pitch bend)
  val channel = Output(UInt(4.W))
  val data1 = Output(UInt(7.W)) // note or CC number
  val data2 = Output(UInt(7.W)) // velocity or CC value
  val held = Output(
    UInt(128.W)
  ) // one hot code for which midi notes are held. `bit n = midi note n currently held` (any channel)
}

/** Top level: UART pin in, decoded events and a held-notes bitmap out. */
class MidiTop(clkHz: Int = Nexys4.clockFrequency, baud: Int = Midi.midiBaudRate)
    extends Module {
  val io = IO(new MidiIO)

  val uart = Module(new UartRx(clkHz, baud))
  val parser = Module(new MidiParser)

  uart.io.rxd := io.rx
  parser.io.in <> uart.io.channel

  val m = parser.io.msg

  val msg = Reg(chiselTypeOf(m.bits))
  val msgVld = RegNext(m.fire, false.B)
  when(m.fire) {msg := m.bits}
  m.ready := true.B

  val kind = msg.status(7, 4)

  // Note-on with velocity 0 means note-off
  val isOn = msgVld && (kind === Midi.noteOn.U && msg.data2 =/= 0.U)
  val isOff = msgVld && (kind === Midi.noteOff.U || (kind === Midi.noteOn.U && msg.data2 === 0.U))

  val held = RegInit(VecInit(Seq.fill(128)(false.B)))
  when(isOn) { held(msg.data1) := true.B }
  when(isOff) { held(msg.data1) := false.B }

  io.noteOn := isOn
  io.noteOff := isOff
  io.cc := msgVld && kind === 0xb.U
  io.bend := msgVld && kind === 0xe.U
  io.channel := msg.status(3, 0)
  io.data1 := msg.data1
  io.data2 := msg.data2
  io.held := held.asUInt
}
