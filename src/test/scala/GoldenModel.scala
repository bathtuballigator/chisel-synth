import math.cos

class GoldenModel(clock_speed: Int, simLength: Int, startNote: Int) {
  println(s"num of steps: ${simLength}, starting note: ${startNote}")

  var size: Int =
    clock_speed / MidiUtils.midiToFreq(startNote + Env.rootMidiNote)
  var acc: Int = 0
  var out: Boolean = false
  var threshold: Int = size >> 1
  // val simTime: Double = simLength.toDouble / Nexys4.clockFrequency
  // val time = List.range(0, simLength).map(x => x / simLength.toDouble * simTime)
  // val freq = notes.map(note => MidiUtils.midiToFreq(note + Env.rootMidiNote))
  // val squareWave = time
  //   .zip(freq)
  //   .map({ case (t, f) =>
  //     (cos(2 * math.Pi * f * t)) > 0
  //   })

  def step(note: Int): Boolean = {
    if (acc == size - 1) {
      acc = 0
      size = clock_speed / MidiUtils.midiToFreq(note + Env.rootMidiNote)
      threshold = size >> 1
    } else {
      acc += 1
    }

    acc >= threshold
  }
  def peek(): Boolean = {
    acc >= threshold
  }
}
