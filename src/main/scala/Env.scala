import chisel3._

object Nexys4 {
    val clockFrequency = 100_000_000  // Hz
    val resetActiveLow = true // true means reset is active when not asserting it
    val numAnodes = 8
}

object Midi {
  val noteOn = 0x9
  val noteOff = 0x8
  val amntMidiNotes = 128
  val midiBaudRate = Env.PcUartBaud  // midi 1.0 uses 31250  // Need to connect through pc for now
}

// To be passed to all modules. Only one instance needed
object Env {
  // -- DEPRECATED in the future --
  val amntFreq: Int =
    16 // number of different frequencies  - thereby also number of switches
  val rootMidiNote = 60  // 60 is C4, then C#4, etc. 69 is A4
  // -- DEPRECATED --

  val polyphonyCount = 1  // how many keys/notes can be played at same time. This scales hardware for each.
  val PcUartBaud = 115200
}
