import chisel3._

class SquareOscillator(
    amp: Int, // needs to be Scala type (Int), not Chisel (UInt)
    sampleWid: Int,
    phaseWid: Int
) extends Module 
{
    // require non-neg values
    require(amp>=0, "Amplitude must not be negative")
    require(sampleWid>0, "Sample width must be positive")
    require(phaseWid>0, "Phase width must be positive")
    require(amp<(1<<(sampleWid-1)), "Amplitude must fit in signed sample width")

    val io=IO(new Bundle
    {
        // chisel hardware unsigned
        val increment = Input(UInt(phaseWid.W))
        // neeeds to be signed
        val sample=Output(SInt(sampleWid.W))
    })
    
    // accumulator
    val acc=RegInit(0.U(phaseWid.W))
    // increment
    acc:=acc+io.increment // overflow will wrap around

    // for square wave, just use MSB
    io.sample:=Mux(acc(phaseWid-1), (-amp).S(sampleWid.W), (+amp).S(sampleWid.W))
}


object OscConfig
{
    def calcIncrement(
        freq:Int,
        sampleRate:Int,
        phaseWid:Int
    ): BigInt=
    {
        require(freq>=0, "Frequency must be non-negative")
        require(freq<sampleRate/2, "Frequency must be less than Nyquist frequency")
        require(sampleRate>0, "Sample rate must be positive")
        require(phaseWid>0, "Phase width must be positive")
        // generated during elaboration phase, so mult/div are not on FPGA
        (BigInt(freq)*(BigInt(1)<<phaseWid))/sampleRate
    }
}
