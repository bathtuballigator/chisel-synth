# Nexys 4 DDR, xc7a100tcsg324-1. Ports match generated/MVP.sv.
set_property CFGBVS VCCO [current_design]
set_property CONFIG_VOLTAGE 3.3 [current_design]
set_property -dict { PACKAGE_PIN E3 IOSTANDARD LVCMOS33 } [get_ports clock]
create_clock -name sys_clk -period 10.000 [get_ports clock]
set_property -dict { PACKAGE_PIN M18 IOSTANDARD LVCMOS33 } [get_ports reset]
set_property -dict { PACKAGE_PIN J15 IOSTANDARD LVCMOS33 } [get_ports io_play]
# Route the raw PWM to LED0 for implementation verification.
# Audio output requires a board wrapper and is deliberately not connected here.
set_property -dict { PACKAGE_PIN H17 IOSTANDARD LVCMOS33 } [get_ports io_out]
# Switch/button inputs are asynchronous. No timing exceptions are applied here:
# inspect check_timing and add synchronization before board deployment.
