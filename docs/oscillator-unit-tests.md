# Oscillator unit tests

Run from the repository root:

```powershell
sbt "testOnly OscillatorWaveformSpec SquareOscSpec OscConfigSpec"
```

`OscillatorWaveformSpec` checks the signed audio samples before the PWM stage.
It uses literal expected vectors rather than calculating expected samples with
the Chisel implementation. With amplitude 15 and a four-bit phase accumulator,
there are 16 phase positions in one complete period.

Each waveform is tested directly and through the `Oscillator` type-selection
wrapper. Checks cover three complete periods, changing increments, skipped phase
positions, wraparound, zero-increment holds, reset during playback, zero
amplitude, and the smallest signed sample width that fits the amplitude.
A separate sine test checks lookup-table addressing when the accumulator has
more bits than the table address.

The expected sine samples use truncation toward zero. Triangle samples include
the current quantization and the repeated endpoints at its turning points.
The expected saw rises from -15 to +15 once per complete phase period, in steps
of two. This follows the README's frequency equation and `OscConfig`: all four
waveforms should have the same fundamental frequency for the same phase width
and increment.

## Result on the original osc implementation

The targeted run completed 38 tests: 33 passed and 5 failed. All five failures
are sawtooth checks. Square, sine, triangle, sine table addressing, zero-amplitude
saw output, and the pre-existing square/config tests pass.

In `SawOscillator`, `acc(phaseWid - 2, 0)` discards the top phase bit. For a
four-bit accumulator, that creates two eight-step ramps during one 16-step
phase cycle. The current denominator also scales an eight-step ramp. For
amplitude 15, phase 1 returns -11 instead of the expected -13; phase 8 repeats
-15 instead of continuing the full-period ramp.

The implementation has not been changed by this test work. Fixing this requires
using the full accumulator for the saw ramp and scaling by
`(BigInt(1) << phaseWid) - 1`. The triangle still needs its folded half-period
ramp and should not receive that same change.

The tests deliberately keep the full-period expectation so they expose the
frequency error rather than accepting it as correct output. The combined test
command will therefore report failure until the saw implementation is corrected.
