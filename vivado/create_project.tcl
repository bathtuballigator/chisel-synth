# Run in Vivado's Tcl Console: source {C:/.../chisel-synth/vivado/create_project.tcl}
set script_dir [file dirname [file normalize [info script]]]
set repo_dir [file dirname $script_dir]
set rtl [file join $repo_dir generated MVP.sv]
if {![file exists $rtl]} { error "Generate MVP.sv first with sbt runMain MVP" }
create_project chisel_synth [file join $script_dir build] -part xc7a100tcsg324-1
set_property target_language Verilog [current_project]
add_files -norecurse [list $rtl]
add_files -fileset constrs_1 -norecurse [list [file join $script_dir MVP.xdc]]
set_property top MVP [get_filesets sources_1]
update_compile_order -fileset sources_1
puts "Project created. Run synthesis, then implementation, from Flow Navigator."
