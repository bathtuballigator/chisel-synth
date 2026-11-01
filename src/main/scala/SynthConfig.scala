import java.nio.file.{Files, Path, Paths}
import scala.io.Source

/** Settings evaluated by Scala before Chisel constructs the hardware. */
case class SynthConfig(amountFreq: Int = 16, rootMidiNote: Int = 69) {
  require(amountFreq >= 1 && amountFreq <= 16,
    "AMOUNT_FREQ must be between 1 and 16 for the Nexys4 switches")
  require(rootMidiNote >= 0 && rootMidiNote <= 127,
    "ROOT_MIDI_NOTE must be between 0 and 127")
  require(rootMidiNote + amountFreq - 1 <= 127,
    "The highest generated MIDI note must not exceed 127")
}

object SynthConfig {
  private val supportedKeys = Set("AMOUNT_FREQ", "ROOT_MIDI_NOTE")

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

    SynthConfig(
      amountFreq = integer("AMOUNT_FREQ", 16),
      rootMidiNote = integer("ROOT_MIDI_NOTE", 69)
    )
  }
}

/** Inspect the selected configuration without generating RTL. */
object ShowSynthConfig extends App {
  println(s"Config file: ${SynthConfig.configuredPath.toAbsolutePath}")
  println(s"AMOUNT_FREQ=${Env.amntFreq}")
  println(s"ROOT_MIDI_NOTE=${Env.rootMidiNote}")
  println(s"MIDI note range: ${Env.rootMidiNote} to ${Env.rootMidiNote + Env.amntFreq - 1}")
}
