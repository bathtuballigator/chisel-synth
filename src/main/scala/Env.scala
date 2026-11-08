object Nexys4 {
  val clockFrequency = 100_000_000 // Hz
  val resetActiveLow = true // true means reset is active when not asserting it
}

object Midi {
  val noteOn = 0x9
  val noteOff = 0x8
  val amntMidiNotes = 128
  val midiBaudRate =
    Env.PcUartBaud // midi 1.0 uses 31250  // Need to connect through pc for now
}

// Loaded once per JVM, before hardware elaboration. Existing module API is retained.
object Env {
  val config: SynthConfig = SynthConfig.load(SynthConfig.configuredPath)
  val voices: Vector[VoiceConfig] = config.voices
  // -- DEPRECATED in the future --
  val amntFreq: Int = config.amountFreq
  // number of different frequencies  - thereby also number of switches
  val rootMidiNote = config.rootMidiNote // 60 is C4, then C#4, etc. 69 is A4
  // -- DEPRECATED --

  val amntFreqPerVoice =
    12 // number of frequencies per voice type. Will go something like 60,61,62,63 for the first voice type, then 64,65,66,67 for the second, etc.

  // Idk how to enumerate the sealed trait
  val voiceList = List(
    OscillatorType.Sine,
    OscillatorType.Square,
    OscillatorType.Saw,
    OscillatorType.Triangle
  )
  val amntVoices = voiceList.length
  val amntFreqTotal = amntFreqPerVoice * amntVoices

  // -- FUTURE --
  val polyphonyCount =
    1 // how many keys/notes can be played at same time. This scales hardware for each.
  // -- FUTURE --

  // -- General Constants --
  val PcUartBaud = 115200
}
