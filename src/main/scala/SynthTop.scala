import chisel3._
import chisel3.util.PopCount

class SynthTopIO extends Bundle {
  val out = Output(Bool())
  val rx = Input(Bool())
  val heldBin = Output(UInt(7.W))
}

class SynthTop(clockFrequency: Int = Nexys4.clockFrequency) extends Module {
  val io = IO(new SynthTopIO())
  val midi = Module(new MidiTop(clkHz = clockFrequency, baud = Midi.midiBaudRate))
  val audio = Module(new SynthAudio(Env.voices, Env.amntFreqPerVoice, clockFrequency))
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
  val midiFrequencies = MidiUtils.midiLookupTable()
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
    synth.io.frequencyHz := midiFrequencies(notes(index))
    busy(index) := gate || synth.io.active
    pwm.io.samples(index) := synth.io.sample
  }

  val reserved = (0 until count).map { i =>
    Mux(busy(i), 1.U(128.W) << notes(i), 0.U)(127, 0)
  }.reduce(_ | _)

  val (pendingNotes, pendingValid) = FirstNSetBits(io.held & ~reserved, count)
  for (i <- 0 until count) {
    when(!busy(i)) {
      // Earlier free slots take the earlier pending notes.
      val rank = if (i == 0) 0.U else PopCount((0 until i).map(j => !busy(j)))
      val valid = if (i == 0) pendingValid(0) else pendingValid(rank)
      val note = if (i == 0) pendingNotes(0) else pendingNotes(rank)
      assigned(i) := valid
      when(valid) { notes(i) := note }
    }
  }

  pwm.io.PWM_en := busy.asUInt.orR
  io.out := pwm.io.PWM_out
}
