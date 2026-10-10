import chisel3._

class MVP extends Module {
  val io = IO(
    new Bundle {
      val on = Input(Bool()) // Whether to play sound or not
      val out = Output(Bool()) // Linked to AUD_PWM
    }
  )

  val squareOsc =
    new SquareOscillator(amp = ???, sampleWid = ???, phaseWid = ???)
}

object MVPConfig {
  def ampToDutyCycle(amp: Int, sampleWid: Int): BigInt = {
    
    var sampleSize = (BigInt(1) << sampleWid)
    var signedMax = sampleSize >> 1
    
/* 

10000
01000

 */
  }
}
