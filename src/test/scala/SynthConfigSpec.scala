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
  }
}
