object Nexys4 {
    val clockFrequency = 100_000_000  // Hz
    val resetActiveLow = true // true means reset is active when not asserting it
}

// Loaded once per JVM, before hardware elaboration. Existing module API is retained.
object Env {
  val config: SynthConfig = SynthConfig.load(SynthConfig.configuredPath)
  val amntFreq: Int = config.amountFreq
  val rootMidiNote: Int = config.rootMidiNote
}
