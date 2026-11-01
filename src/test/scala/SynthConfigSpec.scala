import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SynthConfigSpec extends AnyFlatSpec with Matchers {
  "SynthConfig" should "preserve the existing hardware defaults" in {
    SynthConfig.parse("") shouldBe SynthConfig(16, 69)
  }

  it should "read comments, CRLF, whitespace, quoted values, and a UTF-8 BOM" in {
    SynthConfig.parse(
      "\uFEFF# Example\r\n\r\n AMOUNT_FREQ = \"8\"\r\nROOT_MIDI_NOTE='60'\r\n"
    ) shouldBe SynthConfig(8, 60)
  }

  it should "use defaults for omitted settings" in {
    SynthConfig.parse("ROOT_MIDI_NOTE=60") shouldBe SynthConfig(16, 60)
  }

  it should "reject misspelled and duplicate settings" in {
    intercept[IllegalArgumentException] {
      SynthConfig.parse("AMNT_FREQ=8")
    }.getMessage should include("unsupported key")
    intercept[IllegalArgumentException] {
      SynthConfig.parse("AMOUNT_FREQ=8\nAMOUNT_FREQ=4")
    }.getMessage should include("Duplicate")
  }

  it should "reject malformed lines and noninteger values" in {
    for (text <- Seq("AMOUNT_FREQ", "AMOUNT_FREQ=", "ROOT_MIDI_NOTE=60.5")) {
      intercept[IllegalArgumentException](SynthConfig.parse(text))
    }
  }

  it should "enforce board capacity and MIDI note boundaries" in {
    for (config <- Seq(
      "AMOUNT_FREQ=0", "AMOUNT_FREQ=17", "ROOT_MIDI_NOTE=-1",
      "ROOT_MIDI_NOTE=128", "AMOUNT_FREQ=2\nROOT_MIDI_NOTE=127"
    )) {
      intercept[IllegalArgumentException](SynthConfig.parse(config))
    }
    SynthConfig.parse("AMOUNT_FREQ=1\nROOT_MIDI_NOTE=127") shouldBe SynthConfig(1, 127)
    SynthConfig.parse("AMOUNT_FREQ=16\nROOT_MIDI_NOTE=0") shouldBe SynthConfig(16, 0)
  }

  it should "read an alternate file and report a missing file clearly" in {
    val directory = Files.createTempDirectory("synth-config-test-")
    val file = directory.resolve("custom.env")
    try {
      Files.write(file, "AMOUNT_FREQ=4\nROOT_MIDI_NOTE=60".getBytes(StandardCharsets.UTF_8))
      SynthConfig.load(file) shouldBe SynthConfig(4, 60)
      intercept[IllegalArgumentException] {
        SynthConfig.load(directory.resolve("missing.env"))
      }.getMessage should include("not found")
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(directory)
    }
  }

  it should "load the selected file into the existing Env interface" in {
    Env.config shouldBe SynthConfig.load(SynthConfig.configuredPath)
    Env.amntFreq shouldBe Env.config.amountFreq
    Env.rootMidiNote shouldBe Env.config.rootMidiNote
    Env.voices shouldBe Env.config.voices
  }

  it should "load independent waveform and ADSR settings for three voices" in {
    val config = SynthConfig.load(java.nio.file.Paths.get("config/examples/three-voices.env"))
    config.voices.size shouldBe 3
    config.voices.map(_.waveform) shouldBe
      Vector(Waveform.Square, Waveform.Sawtooth, Waveform.Triangle)
    config.voices(0).envelope shouldBe AdsrConfig(true, 10, 100, BigDecimal("0.7"), 200)
    config.voices(1).envelope shouldBe AdsrConfig(true, 50, 250, BigDecimal("0.5"), 500)
    config.voices(2).envelope shouldBe AdsrConfig(false, 0, 0, BigDecimal(1), 0)
  }

  it should "accept every documented waveform and preserve identical voice settings" in {
    for (waveform <- Waveform.supported) {
      SynthConfig.parse(s"VOICE_0_TYPE=${waveform.name}").voices.head.waveform shouldBe waveform
    }
    val config = SynthConfig.parse("VOICE_COUNT=3\nVOICE_0_TYPE=SINE")
    config.voices.head.waveform shouldBe Waveform.Sine
    config.voices(1) shouldBe config.voices(2)
  }

  it should "reject invalid voice counts and settings for inactive voices" in {
    for (text <- Seq("VOICE_COUNT=0", "VOICE_COUNT=4", "VOICE_COUNT=1\nVOICE_1_TYPE=sine",
        "VOICE_3_TYPE=square", "VOICE_0_TYP=sine", "VOICE_0_TYPE=noise")) {
      intercept[IllegalArgumentException](SynthConfig.parse(text))
    }
  }

  it should "validate ADSR durations, sustain fractions, and explicit enable flags" in {
    for (setting <- Seq(
      "ATTACK_MS=-1", "DECAY_MS=-1", "RELEASE_MS=-1", "ATTACK_MS=0.5",
      "ATTACK_MS=2147483648", "SUSTAIN=-0.1", "SUSTAIN=1.1", "SUSTAIN=NaN",
      "SUSTAIN=Infinity", "ENVELOPE_ENABLED=maybe"
    )) {
      intercept[IllegalArgumentException](SynthConfig.parse(s"VOICE_0_$setting"))
    }
    SynthConfig.parse("VOICE_0_SUSTAIN=0").voices.head.envelope.sustain shouldBe BigDecimal(0)
    SynthConfig.parse("VOICE_0_SUSTAIN=1").voices.head.envelope.sustain shouldBe BigDecimal(1)
  }

  it should "reject duplicate per-voice keys" in {
    intercept[IllegalArgumentException] {
      SynthConfig.parse("VOICE_0_TYPE=square\nVOICE_0_TYPE=sine")
    }.getMessage should include("Duplicate")
  }

  it should "prevent the MVP from silently ignoring unsupported synthesis settings" in {
    SynthConfig.parse("").requireMvpCompatible()
    for (text <- Seq("VOICE_COUNT=2", "VOICE_0_TYPE=sine", "VOICE_0_ENVELOPE_ENABLED=true")) {
      val config = SynthConfig.parse(text)
      intercept[IllegalArgumentException](config.requireMvpCompatible())
        .getMessage should include("integration")
    }
  }
}
