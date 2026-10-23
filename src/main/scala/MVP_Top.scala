import chisel3._
import chisel3.util._


class MVP_Top extends Module {
  val io = IO(new Bundle {
    val play = Input(Bool())
    val AUD_SD = Output(Bool())
    val out = Analog(1.W)
  })

  val mvp = Module(new MVP)
  

}
