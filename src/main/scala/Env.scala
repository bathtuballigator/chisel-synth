import chisel3._

object Nexys4 {
    val clockFrequency = 100_000_000  // Hz
    val useZ = true
    val resetActiveLow = true // true means reset is active when not asserting it
}

// To be passed to all modules. Only one instance needed
object Env {
  val amntFreq: Int =
    7 // number of different frequencies - thereby also number of switches

  // NOTE: Config file would then be read here and variables accordingly
  // Hope this can be done with singleton object as it is now  

}
