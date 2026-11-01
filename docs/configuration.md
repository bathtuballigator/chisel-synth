# Configuration guide and supported-field draft

This implements the configuration-file work in issue #18, with usage documentation
for #30 and a field-format draft for #36. The ADSR implementation is separate work
in #28. This document can be shared at a group meeting or in Discord.

## Current format

The tracked `synth.env` file is the default source. The loader reads UTF-8 text,
including Windows line endings, and accepts:

```dotenv
# Eight consecutive notes, starting at C4.
AMOUNT_FREQ=8
ROOT_MIDI_NOTE=60
```

Blank lines and full-line `#` comments are allowed. Values may be unquoted or
wrapped in matching single or double quotes. Keys are case-sensitive. Each key
may occur only once. Inline comments, `export`, variable expansion, and multiline
values are not supported. This file is parsed as data; it is not run as a script.

Unknown keys, noninteger values, invalid ranges, and missing files cause an error.
Omitted keys use the defaults below. A missing selected file does not silently
use defaults.

## Supported fields

| Key | Default | Meaning and allowed values |
| --- | --- | --- |
| `AMOUNT_FREQ` | `16` | Number of consecutive MIDI notes / switch inputs; integer 1–16. Maps to `Env.amntFreq`. |
| `ROOT_MIDI_NOTE` | `69` | First note; integer 0–127. Maps to `Env.rootMidiNote`. Root + count − 1 must be at most 127. |

The count describes the existing MVP note bank. It is not the number of waveform
voices in the planned three-voice synthesizer. Each note currently uses the
existing square-wave oscillator; changing the root transposes the bank.

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

## Proposed ADSR fields: agreement needed with the envelope team

These are a proposal, not currently accepted config keys. Do not put them into
an active config until the group agrees on the interface and adds loader support.
No envelope state machine, timing logic, or amplitude processing is added here.

Each synthesizer voice should have its own settings, as discussed in issue #28.
Equal settings across multiple voices can still be used in tests. The proposed
names use zero-based voice indices:

```dotenv
# PROPOSED ONLY — not yet supported by the loader.
VOICE_0_ATTACK_MS=10
VOICE_0_DECAY_MS=100
VOICE_0_SUSTAIN=0.7
VOICE_0_RELEASE_MS=200
```

The example numbers are illustrative, not agreed defaults. Repeat the same four
keys with `VOICE_1_` and `VOICE_2_` for the planned additional voices.

Proposed meanings:

| Suffix | Proposed type/unit | Meaning |
| --- | --- | --- |
| `ATTACK_MS` | Nonnegative duration in milliseconds | Time from zero to peak amplitude. |
| `DECAY_MS` | Nonnegative duration in milliseconds | Time from peak to sustain level. |
| `SUSTAIN` | Decimal fraction 0–1 | Held amplitude relative to the peak; not a duration. |
| `RELEASE_MS` | Nonnegative duration in milliseconds | Time from the current level to zero after note-off. |

Before enabling these fields, agree on the envelope update rate, time-to-cycle
rounding, maximum durations, fixed-point sustain representation, zero-duration
behavior, and the mapping between voices and playable notes. The loader should
expose plain Scala configuration data; conversion into hardware counters and
fixed-point values belongs at the agreed envelope integration boundary.

Once agreed, add typed per-voice settings to `SynthConfig`, expose them through
`Env.config`, and pass them into the group's envelope modules during elaboration.
Keep parser validation tests separate from the envelope team's hardware tests.
