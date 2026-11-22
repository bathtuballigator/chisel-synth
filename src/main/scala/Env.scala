object Nexys4 {
  val actualClockFrequency = 100_000_000 // Hz
  val clockFrequency = 25_000_000 // Hz
  val resetActiveLow = true // true means reset is active when not asserting it
  val numAnodes = 8
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
  val amntFreqPerVoice = // deprecated because we have dynamic frequencies
    4 // number of frequencies per voice type. Will go something like 60,61,62,63 for the first voice type, then 64,65,66,67 for the second, etc.
  // -- DEPRECATED --

  val polyphonyCount =
    4 // how many keys/notes can be played at same time of each voice. This scales hardware for each.
  val voiceList = List( // Idk how to enumerate the sealed trait
    OscillatorType.Sine,
    OscillatorType.Square,
    OscillatorType.Saw,
    OscillatorType.Triangle
  )
  val amntVoices = voiceList.length
  val amntSynths = polyphonyCount * amntVoices

  // -- FUTURE --
  // -- FUTURE --

  // -- General Constants --
  val PcUartBaud = 115200
}
