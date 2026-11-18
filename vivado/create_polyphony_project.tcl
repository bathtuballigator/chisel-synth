# Run from Vivado's Tcl Console with source, or use vivado -mode batch -source.
set script_dir [file dirname [file normalize [info script]]]
set repo_dir [file dirname $script_dir]
set rtl [file join $repo_dir generated polyphony.sv]
set xdc [file join $repo_dir xdc_files Nexys4DDR_polyphony.xdc]
set project_dir [file join $script_dir polyphony-build]
set project_file [file join $project_dir chisel_synth_polyphony.xpr]

if {![file isfile $rtl]} { error "Generate polyphony.sv first with sbt runMain Nexys4Gen" }
if {![file isfile $xdc]} { error "Missing constraints: $xdc" }
if {[file exists $project_file]} { error "Project already exists: $project_file" }

create_project chisel_synth_polyphony $project_dir -part xc7a100tcsg324-1
set_property target_language Verilog [current_project]
add_files -norecurse [list $rtl]
add_files -fileset constrs_1 -norecurse [list $xdc]
set_property top Nexys4Wrapper [get_filesets sources_1]
update_compile_order -fileset sources_1
puts "Created $project_file with top [get_property top [get_filesets sources_1]]"
