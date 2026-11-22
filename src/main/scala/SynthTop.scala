import MidiUtils.maxIncrement
import chisel3._
import chisel3.util.PriorityEncoder
import chisel3.util.log2Ceil

class SynthTopIO extends Bundle {
  val out = Output(Bool())
  val rx = Input(Bool())
  val heldBin = Output(UInt(7.W))
}

class SynthTop(
    clockFrequency: Int = Nexys4.clockFrequency,
    midiBaudRate: Int = Midi.midiBaudRate
) extends Module {
  val io = IO(new SynthTopIO())
  val midi = Module(new MidiTop(clkHz = clockFrequency, baud = midiBaudRate))
  val audio = Module(
    new SynthAudio(Env.voices, Env.polyphonyCount, clockFrequency)
  )
  midi.io.rx := io.rx
  audio.io.held := midi.io.held
  io.heldBin := midi.io.data1
  io.out := audio.io.out
}

/** Keep each note in its assigned oscillator slot through its release. Each
  * configured waveform contributes one bank of slots. When all slots are
  * occupied, additional held notes wait for a slot instead of stealing it.
  */
class SynthAudio(
    voices: Seq[VoiceConfig],
    slotsPerVoice: Int,
    clockFrequency: Int,
    sampleWid: Int = 8
) extends Module {
  private val count = voices.size * slotsPerVoice
  require(count >= 1 && count <= 128)
  /* If clock frequency is too low it will create huge incrementers. This approximates largest incrementer val. */
  private var phaseWid = 40
  private val maxInc = maxIncrement(phaseWid, clockFrequency)
  require(
    maxInc.bitLength < phaseWid,
    s"Increase clock Frequency or phaseWid. maxIncrement=${maxInc}=${maxInc.bitLength}.W, phaseWid=$phaseWid"
  )

  val io = IO(new Bundle {
    val held = Input(UInt(128.W))
    val out = Output(Bool())
  })

  val notes = RegInit(VecInit(Seq.fill(count)(0.U(7.W))))
  val assigned = RegInit(VecInit(Seq.fill(count)(false.B)))
  val busy = Wire(Vec(count, Bool()))
  val midiIncrements =
    MidiUtils.midiLookupTable(phaseWid = phaseWid, clkHz = clockFrequency)
  val pwm = Module(new PWMGenerator(sampleWid, count))

  for ((config, bank) <- voices.zipWithIndex; slot <- 0 until slotsPerVoice) {
    val index = bank * slotsPerVoice + slot
    val waveform = config.waveform match {
      case Waveform.Square   => OscillatorType.Square
      case Waveform.Sine     => OscillatorType.Sine
      case Waveform.Sawtooth => OscillatorType.Saw
      case Waveform.Triangle => OscillatorType.Triangle
    }
    val synth = Module(
      new Synth(sampleWid, clockFrequency, waveform, config.envelope)
    )
    val gate = assigned(index) && io.held(notes(index))
    synth.io.gate := gate
    synth.io.increment := midiIncrements(notes(index))
    busy(index) := gate || synth.io.active
    pwm.io.samples(index) := synth.io.sample
  }

  // Use main's decoupled, sequential note selector. Each waveform bank selects
  // the same held notes independently, so all configured voices layer together.
  for (bank <- voices.indices) {
    val indices =
      (0 until slotsPerVoice).map(slot => bank * slotsPerVoice + slot)
    val reserved = indices
      .map { i =>
        Mux(busy(i), 1.U(128.W) << notes(i), 0.U)(127, 0)
      }
      .reduce(_ | _)
    val selector = Module(new FirstNSetBits(n = slotsPerVoice, w = 128))
    selector.io.in.valid := true.B
    selector.io.in.bits := io.held & ~reserved
    selector.io.out.ready := true.B

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
      val allocate = selector.io.out.fire && !busy(index) && remaining(slot).orR
      val chosen = PriorityEncoder(remaining(slot))
      remaining(slot + 1) := Mux(
        allocate,
        remaining(slot) & (remaining(slot) - 1.U),
        remaining(slot)
      )
      when(!busy(index)) {
        assigned(index) := allocate
        when(allocate) { notes(index) := selector.io.out.bits.idx(chosen) }
      }
    }
  }

  pwm.io.PWM_en := busy.asUInt.orR
  io.out := pwm.io.PWM_out
}
