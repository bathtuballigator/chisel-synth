import math.cos

class GoldenModel(simLength: Int, notes: Seq[Int]) {
  println(s"num of steps: ${simLength}, notes: ${notes}")
  require(
    simLength == notes.length,
    "Must have the same number of notes as simulation steps"
  )
  val simTime: Double = simLength.toDouble / Nexys4.clockFrequency
  val time = List.range(0, simLength).map(x => x / simLength.toDouble * simTime)
  val freq = notes.map(note => MidiUtils.midiToFreq(note + Env.rootMidiNote))
  val squareWave = time
    .zip(freq)
    .map({ case (t, f) =>
      (cos(2 * math.Pi * f * t)) > 0
    })

  println(squareWave)
}
