# Group 13 - Parameterizable Live Controlled Synthesizer
=======================
## Dependencies

#### JDK 11 or newer

We recommend using Java 11 or later LTS releases. While Chisel itself works with Java 8, our preferred build tool Mill requires Java 11. You can install the JDK as your operating system recommends, or use the prebuilt binaries from [Adoptium](https://adoptium.net/) (formerly AdoptOpenJDK).

#### SBT or mill

SBT is the most common build tool in the Scala community. You can download it [here](https://www.scala-sbt.org/download.html).
Mill is another Scala/Java build tool preferred by Chisel's developers.
This repository includes a bootstrap script `./mill` so that no installation is necessary.
You can read more about Mill on its website: https://mill-build.org.

#### Verilator

The test with `svsim` needs Verilator installed.
See Verilator installation instructions [here](https://verilator.org/guide/latest/install.html).


## Synthesizer configuration

Edit [`synth.env`](synth.env) before generating hardware. It uses a small `.env`
format: one `KEY=VALUE` per line, with full-line `#` comments and optional quotes.
No additional library or shell command is needed to load it.

```dotenv
AMOUNT_FREQ=16
ROOT_MIDI_NOTE=69
VOICE_COUNT=1
VOICE_0_TYPE=square
VOICE_0_ENVELOPE_ENABLED=false
```

`AMOUNT_FREQ` selects 1 to 16 consecutive note/switch inputs; `ROOT_MIDI_NOTE`
selects their first MIDI note (60 = C4, 69 = A4). The last note must not exceed
127. `VOICE_COUNT` describes 1 to 3 synthesizer voices. Each `VOICE_n_TYPE` selects
`square`, `sine`, `sawtooth`, or `triangle`. Each voice can enable an envelope and
set its attack/decay/release in whole milliseconds and sustain as a 0–1 fraction.
Missing keys retain defaults. The checked-in config currently uses four notes
starting at C4; the loader defaults remain 16 notes starting at A4.

The current MVP hardware consumes the note-bank settings and supports one square
voice without an envelope. The loader accepts the complete voice format, but
hardware generation rejects unsupported voice selections until the oscillator
and ADSR teams integrate their modules. It will not silently ignore them.

From the repository root, check the selected values and generate the board design:

```text
sbt "runMain ShowSynthConfig"
sbt "runMain Nexys4Gen"
```

To select another file in PowerShell:

```powershell
$env:SYNTH_CONFIG = "C:\path\to\custom.env"
sbt "runMain ShowSynthConfig"
sbt "runMain Nexys4Gen"
Remove-Item Env:SYNTH_CONFIG
```

Restart sbt after changing the file: `Env` is a singleton loaded once per JVM.
Regenerate RTL and rebuild the FPGA after changes. Fewer note inputs also require
matching XDC constraints; the supplied octave constraints describe 16 switches.

See [the complete configuration format](docs/configuration.md) for all fields,
defaults, and the Scala interface for the oscillator and envelope teams.
[The three-voice example](config/examples/three-voices.env) shows independent
waveforms and envelopes. The configuration data is implemented; waveform mixing,
MIDI input, chord handling, and ADSR hardware remain separate group tasks.

## File Structure

`.xdc` files are saved in `xdc_files/`. In case inputs change between branches etc. we want to keep them fairly tied to a feature.

The generated system verilog files are in `generated/`

The rest is typical chisel file structure

>  ```
> my-project/
> ├── build.sbt
> ├── project/
> │   └── build.properties
> ├── src/
> │   ├── main/
> │   │   └── scala/
> │   │       └── mypackage/      # all lowercase, no underscores
> │   │           ├── MyTop.scala # UpperCamelCase, matches top-level class
> │   │           ├── MyModule.scala
> │   │           └── MyBundle.scala
> │   └── test/
> │       └── scala/
> │           └── mypackage/          # mirrors main package structure
> │               ├── MyTopSpec.scala
> │               └── MyModuleSpec.scala
> ├── .gitignore
> └── README.md   
> ```

## Project Description
This project will use chisel generators, combined with a spreadsheet of required components, to generate a custom hardware synthesizer as defined in the spreadsheet. The synthesizer will have a maximum of 3 voices, which the user can define as sine, square, sawtooth, or triangle. The user can also define an envelope (Attack, Decay, Sustain, and Release parameters) for each voice.

The synthesizer can be connected to a MIDI keyboard, and played live, or it can be connected to a computer and play a sequence of pre-programmed notes.

## Glossary

| Term                   | shorthand | Description                                                                                                                                                    |
| ---------------------- | --------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Amplitude              | amp       | Peak magnitude of a wave; perceived as loudness                                                                                                                |
| Sample rate            | fs        | Samples produced per second (Hz)                                                                                                                               |
| Sample                 |           | Signed int value of the audio wave at one point in time; one per sample period                                                                                 |
| Phase                  |           | Current position within one wave period, 0 to 2π (or 0 to max of the phase counter)                                                                            |
| Phase accumulator      |           | Counter incremented by the tuning word each sample; its value is the phase                                                                                     |
| Tuning word            |           | Phase increment per sample; sets output frequency: $f = inc \cdot fs / 2^N (N = acc.width)$                                                                    |
| Nyquist frequency      | Nyquist   | fs / 2; highest frequency representable without aliasing                                                                                                       |
| Aliasing               |           | Content above Nyquist folding back as false lower frequencies                                                                                                  |
| Pulse-Width-Modulation | pwm       | 1-bit output whose average (duty cycle) encodes an analog level                                                                                                |
| Duty cycle             |           | Fraction of a PWM period the output is high                                                                                                                    |
| Low-pass filter        | LPF       | Attenuates frequencies above a cutoff; turns PWM into an analog waveform                                                                                       |
| pwm frequency          | pwm       | The frequency of off and on-duty cycle. `counter_size` in PWM. Should be 10 times the desired audio frequency (sample rate) $\text{counter\_size} \propto sampleWid$. |
| Oscillator             | osc       | Generates a periodic waveform (sine, square, sawtooth, triangle) at a given frequency                                                                          |
| Voice                  |           | One independently playable sound: oscillator + envelope                                                                                                        |
| Envelope               | ADSR      | Amplitude shape over a note's life: Attack, Decay, Sustain, Release                                                                                            |
| Attack                 | A         | Time to rise from 0 to peak after note-on                                                                                                                      |
| Decay                  | D         | Time to fall from peak to sustain level                                                                                                                        |
| Sustain                | S         | Level (not time) held while the note is on                                                                                                                     |
| Release                | R         | Time to fall from sustain to 0 after note-off                                                                                                                  |
| MIDI                   |           | Serial protocol (31.25 kbaud) for note-on/off, pitch and velocity messages                                                                                     |




## Nexys DDR4 Audio Output Specs

-  Pin A11 is connected to AUD_PWM, which is  the input to an analog low-pass filter$[^1]$
![plot of filtering strength in proportion to frequency of a low-pass filter](docs/ddr4_output_filtering.png)

> [!CAUTION]
> Input should be low impedance `Z` for logic `1`. For logic `0` output should not be driven.

generating a signal is as simple as connecting the output to a PWM generator and setting the duty cycle of the PWM to what frequency we want to produce. sample verilog code included below

```verilog
module pwm_module( 
input clk,
input [10:0] PWM_in, 
output reg PWM_out
);

reg [10:0]new_pwm=0;
reg [10:0] PWM_ramp=0; 
always @(posedge clk) 
begin
    if (PWM_ramp==0)new_pwm<=PWM_in;
      PWM_ramp <= PWM_ramp + 1'b1;
      PWM_out<=(new_pwm>PWM_ramp);
end

endmodule
```

- no hard specs on what frequencies need to be met by hardware, the only caveat is that the datasheet recommends that our PWM frequency be at least one order of magnitude higher than anything we want the audio output to produce. using a system clock of 100MHz to drive the PWM should meet this constraint with plenty of room. 





## Interfaces

### PWM
```
PWM(counter_size):
  width = log2Ceil(counter_size)
  io = (
    PWM_out = Output(Bool())
    PWM_in = Input(UInt(width.W))
  )
```
### Oscillator

```
SquareOscillator(
    amp: Int, 
    sampleWid: Int, 
    phaseWid: Int 
):
    io = (
        increment = Input(UInt(phaseWid.W)) 
        sample=Output(SInt(sampleWid.W)) 
    )
    
```

### MVP

```
MVP(
    amp: Int = 256,
    sampleWid: Int = 8,
    phaseWid: Int = 16,
    frequency: Int = 440, // Frequency to play square wave in Hz,
    sampleRate: Int = 48_000
):
  io = (
    play = Input(Bool()) // Whether to play sound or not
    out = Output(Bool()) // Linked to AUD_PWM
  )
```

### Connection graph

![image](docs/interfaces.drawio.svg)

## Sources

[^1] https://digilent.com/reference/programmable-logic/nexys-4-ddr/reference-manual

[^2] https://digilent.com/reference/programmable-logic/nexys-4-ddr/reference-manual#pulse-width_modulation

