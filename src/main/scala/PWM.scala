import chisel3._
import chisel3.util.log2Ceil

class PWM(counter_size: Int) extends Module {

  val width: Int = log2Ceil(counter_size)

  val io = IO(new Bundle {
    val PWM_out = Output(Bool())
    val PWM_in = Input(UInt(width.W))
  })

  val acc = RegInit(0.U(width.W))
  val PWM_out_reg = RegInit(0.B)
  val PWM_thresh = RegInit(counter_size.U(width.W))

  acc := Mux(acc === counter_size.U, 0.U, acc + 1.U)
  PWM_thresh := Mux(acc === 0.U, io.PWM_in, PWM_thresh)
  PWM_out_reg := acc >= PWM_thresh
  io.PWM_out := PWM_out_reg

}
