import chisel3._
import chisel3.util.{PriorityEncoder, log2Ceil}

class SynthTopIO extends Bundle {
  val out = Output(Bool())
  val rx = Input(Bool())
  val heldBin = Output(UInt(7.W))
}

class SynthTop(clockFrequency: Int = Nexys4.clockFrequency) extends Module {
  val io = IO(new SynthTopIO())
  val midi = Module(new MidiTop(clkHz = clockFrequency, baud = Midi.midiBaudRate))
  val audio = Module(new SynthAudio(Env.voices, Env.polyphonyCount, clockFrequency))
  midi.io.rx := io.rx
  audio.io.held := midi.io.held
  io.heldBin := midi.io.data1
  io.out := audio.io.out
}

/** Keep each note in its assigned oscillator slot through its release.
  * Each configured waveform contributes one bank of slots. When all slots
  * are occupied, additional held notes wait for a slot instead of stealing it.
  */
class SynthAudio(
    voices: Seq[VoiceConfig],
    slotsPerVoice: Int,
    clockFrequency: Int,
    sampleWid: Int = 8
) extends Module {
  private val count = voices.size * slotsPerVoice
  require(count >= 1 && count <= 128)

  val io = IO(new Bundle {
    val held = Input(UInt(128.W))
    val out = Output(Bool())
  })

  val notes = RegInit(VecInit(Seq.fill(count)(0.U(7.W))))
  val assigned = RegInit(VecInit(Seq.fill(count)(false.B)))
  val busy = Wire(Vec(count, Bool()))
  val midiIncrements = MidiUtils.midiLookupTable(phaseWid = 32, clkHz = clockFrequency)
  val pwm = Module(new PWMGenerator(sampleWid, count))

  for ((config, bank) <- voices.zipWithIndex; slot <- 0 until slotsPerVoice) {
    val index = bank * slotsPerVoice + slot
    val waveform = config.waveform match {
      case Waveform.Square   => OscillatorType.Square
      case Waveform.Sine     => OscillatorType.Sine
      case Waveform.Sawtooth => OscillatorType.Saw
      case Waveform.Triangle => OscillatorType.Triangle
    }
    val synth = Module(new Synth(sampleWid, clockFrequency, waveform, config.envelope))
    val gate = assigned(index) && io.held(notes(index))
    synth.io.gate := gate
    synth.io.increment := midiIncrements(notes(index))
    busy(index) := gate || synth.io.active
    pwm.io.samples(index) := synth.io.sample
  }

  // One selector services all banks; hold its bank until the response arrives.
  val bankReservations = Wire(Vec(voices.size, UInt(128.W)))
  for (bank <- voices.indices) {
    val indices = (0 until slotsPerVoice).map(slot => bank * slotsPerVoice + slot)
    bankReservations(bank) := indices.map { i =>
      Mux(busy(i), 1.U(128.W) << notes(i), 0.U)(127, 0)
    }.reduce(_ | _)
  }
  val selectedBank = RegInit(0.U(math.max(1, log2Ceil(voices.size)).W))
  val selector = Module(new FirstNSetBits(n = slotsPerVoice, w = 128))
  selector.io.in.valid := true.B
  val selectedReservations = if (voices.size == 1) bankReservations(0)
    else bankReservations(selectedBank)
  selector.io.in.bits := io.held & ~selectedReservations
  selector.io.out.ready := true.B
  when(selector.io.out.fire) {
    selectedBank := Mux(selectedBank === (voices.size - 1).U, 0.U, selectedBank + 1.U)
  }

  for (bank <- voices.indices) {
    val indices = (0 until slotsPerVoice).map(slot => bank * slotsPerVoice + slot)
    val reserved = bankReservations(bank)

    // Selection takes multiple clocks. Recheck live gates/reservations when
    // consuming a response so a note released during selection cannot start.
    val candidates = Wire(Vec(slotsPerVoice, Bool()))
    for (slot <- 0 until slotsPerVoice) {
      val note = selector.io.out.bits.idx(slot)
      candidates(slot) := selector.io.out.bits.valid(slot) &&
        io.held(note) && !reserved(note)
    }
    val remaining = Wire(Vec(slotsPerVoice + 1, UInt(slotsPerVoice.W)))
    remaining(0) := candidates.asUInt
    for ((index, slot) <- indices.zipWithIndex) {
      val allocate = selector.io.out.fire && selectedBank === bank.U && !busy(index) && remaining(slot).orR
      val chosen = PriorityEncoder(remaining(slot))
      remaining(slot + 1) := Mux(allocate,
        remaining(slot) & (remaining(slot) - 1.U), remaining(slot))
      when(!busy(index)) {
        assigned(index) := allocate
        when(allocate) { notes(index) := selector.io.out.bits.idx(chosen) }
      }
    }
  }

  pwm.io.PWM_en := busy.asUInt.orR
  io.out := pwm.io.PWM_out
}
