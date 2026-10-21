# Nexys 4 DDR implementation check

The project targets `xc7a100tcsg324-1`, with `MVP` as the top module.
The checked-in `generated/MVP.sv` contains all three modules. Regenerate it
from the repository root using `sbt "runMain MVP"` (JDK 11 or newer).
The generator uses amplitude 256 and a 16-bit sample width to satisfy the
existing elaboration requirements; module defaults and test parameters are unchanged.

In Vivado, choose **Tools > Run Tcl Script** and select `create_project.tcl`
from this directory. It creates `build/chisel_synth.xpr`. Alternatively,
add `generated/MVP.sv` as a SystemVerilog design source, add `MVP.xdc`
as constraints, and set the top module to `MVP`.

Run synthesis, then implementation, and inspect the routed timing summary
and DRC reports. Build outputs are ignored by Git.

## Pin mapping

| Port | Board connection |
| --- | --- |
| clock | E3, 100 MHz oscillator |
| reset | M18, BTNU |
| io_play | J15, SW0 |
| io_out | H17, LED0 (raw PWM) |

## Reported verification results

The routed Vivado 2023.1 reports supplied on 21 October 2026 show:

- Setup WNS: +5.999 ns; TNS: 0 ns.
- Hold WHS: +0.092 ns; THS: 0 ns.
- No failing setup/hold endpoints or unconstrained internal endpoints.
- Fully routed DRC: zero violations.

These results cover constrained internal timing at 100 MHz. Two input delays
and one output delay remain unspecified. Input synchronization, external I/O
timing, and functional audio/sample timing still need verification. This setup
uses LED0, not the audio jack, and does not establish correct audio behavior.
