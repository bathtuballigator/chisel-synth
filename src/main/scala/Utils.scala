object MidiUtils {
  def midiToFreq(note: Int): // 440 Hz is midi note 69
  Int = (440.0 * math.pow(2, (note - 69) / 12.0)).toInt
  def freqToMidi(freq: Double): Int =
    (69 + 12 * math.log(freq / 440.0) / math.log(2)).toInt
}
