// Created with Sonnet 5.5
import chisel3._
import circt.stage.ChiselStage

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

// Helper for generating Vivado-ready SystemVerilog from a Chisel module.
// Usage in an App object:
//   object MyGen extends App { VerilogGen(new MyModule, "MyModule.sv") }
object VerilogGen {
  // emitVerilog appends a file list for inline black boxes, which is not
  // valid SystemVerilog, so everything from this marker on is dropped.
  private val blackBoxFileList =
    "// ----- 8< ----- FILE \"firrtl_black_box_resource_files.f\""

  def apply(
      gen: => RawModule,
      fileName: String,
      outDir: String = "generated"
  ): Path = {
    val output = Paths.get(outDir, fileName)
    Files.createDirectories(output.getParent)
    val verilog = ChiselStage.emitSystemVerilog(gen).split(blackBoxFileList).head
    Files.write(output, verilog.getBytes(StandardCharsets.UTF_8))


    println(s"Verilog written to ${output.toAbsolutePath}")
    output
  }
}
