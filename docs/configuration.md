# Configuration guide and voice format

This implements the configuration-file work in issue #18, with usage documentation
for #30 and a parsed, validated voice format for #36. The ADSR implementation is separate work
in #28. This document can be shared at a group meeting or in Discord.

## Current format

The tracked `synth.env` file is the default source. The loader reads UTF-8 text,
including Windows line endings, and accepts:

```dotenv
# Eight consecutive notes, starting at C4.
AMOUNT_FREQ=8
ROOT_MIDI_NOTE=60
VOICE_COUNT=1
VOICE_0_TYPE=square
VOICE_0_ENVELOPE_ENABLED=false
```

Blank lines and full-line `#` comments are allowed. Values may be unquoted or
wrapped in matching single or double quotes. Keys are case-sensitive. Each key
may occur only once. Inline comments, `export`, variable expansion, and multiline
values are not supported. This file is parsed as data; it is not run as a script.

Unknown keys, invalid values or ranges, duplicate keys, and missing files cause an error.
Omitted keys use the defaults below. A missing selected file does not silently
use defaults.

## Supported fields

| Key | Default | Meaning and allowed values |
| --- | --- | --- |
| `AMOUNT_FREQ` | `16` | Number of consecutive MIDI notes / switch inputs; integer 1–16. Maps to `Env.amntFreq`. |
| `ROOT_MIDI_NOTE` | `69` | First note; integer 0–127. Maps to `Env.rootMidiNote`. Root + count − 1 must be at most 127. |
| `VOICE_COUNT` | `1` | Number of synthesizer voices; integer 1–3. |

The count describes the existing MVP note bank. It is not the number of waveform
voices in the planned three-voice synthesizer. Each note currently uses the
existing square-wave oscillator; changing the root transposes the bank. A voice
describes a waveform plus its envelope, while the note bank describes pitches.
The configuration does not select a chord algorithm or implement polyphonic mixing.

The Nexys4 clock frequency and reset polarity stay in the board definition
`Nexys4`. They are deliberately not configurable here because the RTL and board
timing constraints must agree.

## Loading and generating

The loader checks, in order:

1. JVM system property `synth.config`.
2. Environment variable `SYNTH_CONFIG`.
3. `synth.env` relative to the process working directory.

For a terminal workflow, run sbt from the repository root. In an IDE, set the run
configuration's working directory to that root or pass an absolute file path.

```powershell
sbt "runMain ShowSynthConfig"
sbt "testOnly SynthConfigSpec SynthConfigHardwareSpec"
sbt "runMain Nexys4Gen"
```

For another configuration, set `SYNTH_CONFIG` before starting sbt:

```powershell
$env:SYNTH_CONFIG = "C:\path\to\custom.env"
sbt "runMain ShowSynthConfig"
sbt "runMain Nexys4Gen"
Remove-Item Env:SYNTH_CONFIG
```

`ShowSynthConfig` only prints settings. `Nexys4Gen` writes
`generated/MVP_Octave.sv`, whose board top module is `Nexys4Wrapper`.
The board constraints are in `xdc_files/Nexys4DDR_octave.xdc`. That file maps all
16 switch inputs; if `AMOUNT_FREQ` is smaller, use a copy with constraints only
for the generated input ports. No pin constraints are generated automatically.

Scala reads the configuration once when `Env` is initialized. Restart the sbt JVM
to reload edits, then regenerate SystemVerilog, synthesize, and program the FPGA.
These values do not change a running FPGA's settings.

To repeat the alternate-file integration check, set `SYNTH_CONFIG` to
`src/test/resources/four-notes.env` and run the two config test suites. This checks
a four-input bank starting at C4, including the highest note's generated period.

## Voice types and ADSR fields

The full voice format is now accepted by the loader. These units and defaults
define the config-file interface; they are not a claim that the envelope team's
hardware interface has been finalized. No envelope state machine or waveform
implementation is added here.

Each synthesizer voice has its own settings, as discussed in issue #28. Equal
settings across multiple voices can still be used in tests. Indices start at zero
and must be smaller than `VOICE_COUNT`. Keys for inactive voices are rejected.

```dotenv
VOICE_COUNT=2
VOICE_0_TYPE=square
VOICE_0_ENVELOPE_ENABLED=true
VOICE_0_ATTACK_MS=10
VOICE_0_DECAY_MS=100
VOICE_0_SUSTAIN=0.7
VOICE_0_RELEASE_MS=200

VOICE_1_TYPE=sawtooth
VOICE_1_ENVELOPE_ENABLED=true
VOICE_1_ATTACK_MS=50
VOICE_1_DECAY_MS=250
VOICE_1_SUSTAIN=0.5
VOICE_1_RELEASE_MS=500
```

Repeat the suffixes for `VOICE_0_`, `VOICE_1_`, and `VOICE_2_` as needed. Missing
keys receive the defaults below. The sustain setting is a level, not a duration.

Supported per-voice fields:

| Suffix | Default | Type/unit and meaning |
| --- | --- | --- |
| `TYPE` | `square` | `square`, `sine`, `sawtooth`, or `triangle`; values are case-insensitive. |
| `ENVELOPE_ENABLED` | `false` | `true` or `false`; selects envelope processing or bypass when integrated. |
| `ATTACK_MS` | `10` | Nonnegative whole milliseconds from zero to peak amplitude. |
| `DECAY_MS` | `100` | Nonnegative whole milliseconds from peak to sustain level. |
| `SUSTAIN` | `0.7` | Decimal fraction 0–1 of peak amplitude. |
| `RELEASE_MS` | `200` | Nonnegative whole milliseconds from the current level to zero after note-off. |

Durations must fit a signed Scala `Int` (0 to 2147483647 ms). Decimal sustain is
stored as `BigDecimal`, not binary floating point. Disabled envelopes retain
their parameters as data, so they can be enabled later without losing settings.
Zero-duration values are allowed by the format; their hardware bypass behavior
must be defined by the envelope team. Negative durations, unknown waveforms,
invalid booleans, and sustain outside 0–1 are rejected.

## Inspecting multiple voices

The checked-in `config/examples/three-voices.env` is a complete format example.
To inspect it without generating hardware:

```powershell
$env:SYNTH_CONFIG = "config/examples/three-voices.env"
sbt "runMain ShowSynthConfig"
Remove-Item Env:SYNTH_CONFIG
```

The output lists each voice's waveform, envelope enable flag, and all four ADSR
parameters. Changing `TYPE` changes the parsed waveform selection. Setting
`ENVELOPE_ENABLED=true` enables that voice's envelope in the configuration model.

## Integration boundary for the group

`Env.voices` and `Env.config.voices` expose a `Vector[VoiceConfig]`. For example:

```scala
val voice = Env.voices(0)
val waveform: Waveform = voice.waveform
val adsr: AdsrConfig = voice.envelope
// adsr.enabled, adsr.attackMs, adsr.decayMs, adsr.sustain, adsr.releaseMs
```

`Waveform` is a sealed Scala type with `Square`, `Sine`, `Sawtooth`, and `Triangle`
cases. `VoiceConfig` and `AdsrConfig` are plain Scala data. No Chisel state is
constructed by the parser. The oscillator wrapper can select a module from the
waveform value, and the envelope team can receive the matching `AdsrConfig`.

The current `MVP_Top` accepts one square voice with its envelope disabled. It
calls `requireMvpCompatible()` and fails clearly for unsupported voice configs.
This prevents generating the same old RTL after selecting three voices, another
waveform, or enabled envelopes. The group's integrated top should replace this
MVP restriction with actual per-voice construction and mixing when ready.

Agree on envelope update rate, time-to-cycle rounding, fixed-point sustain,
zero-duration behavior, and the mapping between voices and notes during hardware
integration. Those conversions belong to the hardware modules, not the loader.
MIDI/keyboard input and chord handling shown on the project board are independent
features; no unsupported live-control behavior is implied by this config format.
