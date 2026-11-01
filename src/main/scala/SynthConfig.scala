import java.nio.file.{Files, Path, Paths}
import scala.io.Source

sealed trait Waveform { def name: String }

object Waveform {
  case object Square extends Waveform { val name = "square" }
  case object Sine extends Waveform { val name = "sine" }
  case object Sawtooth extends Waveform { val name = "sawtooth" }
  case object Triangle extends Waveform { val name = "triangle" }

  val supported: Vector[Waveform] = Vector(Square, Sine, Sawtooth, Triangle)

  def parse(value: String): Waveform =
    supported.find(_.name == value.toLowerCase(java.util.Locale.ROOT)).getOrElse {
      throw new IllegalArgumentException(
        s"Unknown waveform '$value'; use ${supported.map(_.name).mkString(", ")}")
    }
}

/** Plain Scala settings for the envelope team's future hardware integration. */
case class AdsrConfig(
    enabled: Boolean = false,
    attackMs: Int = 10,
    decayMs: Int = 100,
    sustain: BigDecimal = BigDecimal("0.7"),
    releaseMs: Int = 200
) {
  require(attackMs >= 0, "ATTACK_MS must not be negative")
  require(decayMs >= 0, "DECAY_MS must not be negative")
  require(releaseMs >= 0, "RELEASE_MS must not be negative")
  require(sustain >= 0 && sustain <= 1, "SUSTAIN must be between 0 and 1")
}

case class VoiceConfig(
    waveform: Waveform = Waveform.Square,
    envelope: AdsrConfig = AdsrConfig()
)

/** Settings evaluated by Scala before Chisel constructs the hardware. */
case class SynthConfig(
    amountFreq: Int = 16,
    rootMidiNote: Int = 69,
    voices: Vector[VoiceConfig] = Vector(VoiceConfig())
) {
  require(amountFreq >= 1 && amountFreq <= 16,
    "AMOUNT_FREQ must be between 1 and 16 for the Nexys4 switches")
  require(rootMidiNote >= 0 && rootMidiNote <= 127,
    "ROOT_MIDI_NOTE must be between 0 and 127")
  require(rootMidiNote + amountFreq - 1 <= 127,
    "The highest generated MIDI note must not exceed 127")
  require(voices.nonEmpty && voices.size <= 3, "VOICE_COUNT must be between 1 and 3")

  /** Prevent the current square-wave MVP from silently ignoring future settings. */
  def requireMvpCompatible(): Unit = {
    require(voices.size == 1 && voices.head.waveform == Waveform.Square &&
      !voices.head.envelope.enabled,
      "The current MVP supports one square voice with its envelope disabled. " +
        "The config format supports multiple voices and ADSR, but the group's " +
        "oscillator/envelope integration is still required. Use ShowSynthConfig to inspect it.")
  }
}

object SynthConfig {
  private val voiceSuffixes = Set(
    "TYPE", "ENVELOPE_ENABLED", "ATTACK_MS", "DECAY_MS", "SUSTAIN", "RELEASE_MS")
  private val supportedKeys = Set("AMOUNT_FREQ", "ROOT_MIDI_NOTE", "VOICE_COUNT") ++
    (for (index <- 0 until 3; suffix <- voiceSuffixes) yield s"VOICE_${index}_$suffix")

  /** File path precedence: JVM property, environment variable, repository default. */
  def configuredPath: Path = Paths.get(
    sys.props.get("synth.config")
      .orElse(sys.env.get("SYNTH_CONFIG"))
      .getOrElse("synth.env")
  )

  def load(path: Path): SynthConfig = {
    require(Files.isRegularFile(path),
      s"Synth config file not found: ${path.toAbsolutePath}. " +
        "Run from the repository root or set SYNTH_CONFIG.")
    val source = Source.fromFile(path.toFile, "UTF-8")
    try {
      parse(source.mkString)
    } catch {
      case error: IllegalArgumentException =>
        throw new IllegalArgumentException(
          s"Invalid synth config ${path.toAbsolutePath}: ${error.getMessage}", error)
    } finally {
      source.close()
    }
  }

  /** A small .env subset: KEY=VALUE, blank lines, full-line # comments, quotes. */
  def parse(text: String): SynthConfig = {
    val entries = text.stripPrefix("\uFEFF").linesIterator.zipWithIndex.flatMap {
      case (rawLine, index) =>
        val line = rawLine.trim
        if (line.isEmpty || line.startsWith("#")) {
          None
        } else {
          val separator = line.indexOf('=')
          require(separator > 0, s"Line ${index + 1}: expected KEY=VALUE")
          val key = line.take(separator).trim
          require(supportedKeys.contains(key),
            s"Line ${index + 1}: unsupported key '$key'")
          val rawValue = line.drop(separator + 1).trim
          val value =
            if (rawValue.length >= 2 &&
                ((rawValue.head == '"' && rawValue.last == '"') ||
                 (rawValue.head == '\'' && rawValue.last == '\''))) {
              rawValue.substring(1, rawValue.length - 1)
            } else rawValue
          Some(key -> value)
        }
    }.toVector

    val duplicates = entries.groupBy(_._1).collect {
      case (key, values) if values.size > 1 => key
    }
    require(duplicates.isEmpty,
      s"Duplicate settings: ${duplicates.toSeq.sorted.mkString(", ")}")

    val settings = entries.toMap
    def integer(key: String, default: Int): Int = settings.get(key) match {
      case None => default
      case Some(value) => value.toIntOption.getOrElse {
        throw new IllegalArgumentException(s"$key must be an integer, got '$value'")
      }
    }

    def boolean(key: String, default: Boolean): Boolean = settings.get(key) match {
      case None => default
      case Some(value) => value.toLowerCase(java.util.Locale.ROOT) match {
        case "true" => true
        case "false" => false
        case _ => throw new IllegalArgumentException(s"$key must be true or false, got '$value'")
      }
    }

    def decimal(key: String, default: BigDecimal): BigDecimal = settings.get(key) match {
      case None => default
      case Some(value) =>
        try { BigDecimal(value) }
        catch {
          case _: NumberFormatException =>
            throw new IllegalArgumentException(s"$key must be a decimal, got '$value'")
        }
    }

    val voiceCount = integer("VOICE_COUNT", 1)
    require(voiceCount >= 1 && voiceCount <= 3, "VOICE_COUNT must be between 1 and 3")
    for (index <- voiceCount until 3; suffix <- voiceSuffixes) {
      require(!settings.contains(s"VOICE_${index}_$suffix"),
        s"VOICE_${index}_$suffix refers to an inactive voice; increase VOICE_COUNT or remove it")
    }

    val voices = Vector.tabulate(voiceCount) { index =>
      val prefix = s"VOICE_${index}_"
      try {
        VoiceConfig(
          waveform = Waveform.parse(settings.getOrElse(prefix + "TYPE", "square")),
          envelope = AdsrConfig(
            enabled = boolean(prefix + "ENVELOPE_ENABLED", false),
            attackMs = integer(prefix + "ATTACK_MS", 10),
            decayMs = integer(prefix + "DECAY_MS", 100),
            sustain = decimal(prefix + "SUSTAIN", BigDecimal("0.7")),
            releaseMs = integer(prefix + "RELEASE_MS", 200)
          )
        )
      } catch {
        case error: IllegalArgumentException =>
          throw new IllegalArgumentException(s"Voice $index: ${error.getMessage}", error)
      }
    }

    SynthConfig(
      amountFreq = integer("AMOUNT_FREQ", 16),
      rootMidiNote = integer("ROOT_MIDI_NOTE", 69),
      voices = voices
    )
  }
}

/** Inspect the selected configuration without generating RTL. */
object ShowSynthConfig extends App {
  println(s"Config file: ${SynthConfig.configuredPath.toAbsolutePath}")
  println(s"AMOUNT_FREQ=${Env.amntFreq}")
  println(s"ROOT_MIDI_NOTE=${Env.rootMidiNote}")
  println(s"MIDI note range: ${Env.rootMidiNote} to ${Env.rootMidiNote + Env.amntFreq - 1}")
  println(s"VOICE_COUNT=${Env.voices.size}")
  for ((voice, index) <- Env.voices.zipWithIndex) {
    val prefix = s"VOICE_${index}_"
    println(s"${prefix}TYPE=${voice.waveform.name}")
    println(s"${prefix}ENVELOPE_ENABLED=${voice.envelope.enabled}")
    println(s"${prefix}ATTACK_MS=${voice.envelope.attackMs}")
    println(s"${prefix}DECAY_MS=${voice.envelope.decayMs}")
    println(s"${prefix}SUSTAIN=${voice.envelope.sustain}")
    println(s"${prefix}RELEASE_MS=${voice.envelope.releaseMs}")
  }
  if (Env.voices.size != 1 || Env.voices.head.waveform != Waveform.Square ||
      Env.voices.head.envelope.enabled) {
    println("These voice settings load successfully, but require the group's " +
      "oscillator/envelope integration before the MVP can generate them.")
  }
}
