import chisel3._

class Oscillator(
    amp: UInt,
    sampleRate: UInt,
    sampleWid: UInt,
    phaseWid: UInt
) extends Module 
{
    val io=IO(new Bundle
    {
        val increment = Input(UInt(phaseWid.W))
        // neeeds to be signed
        val sample=Output(SInt(sampleWid.W))
    })
    
    // accumulator
    val acc=RegInit(0.U(phaseWid.W))
    // increment
    acc:=acc+io.increment

    // for square wave, just use MSB
    io.sample:=Mux(phase(phaseWid-1), (-amp).S((sampleWid)), (+amp).S((sampleWid)))

    return io.sample
}


object Osc
{
    def calcIncrement(
        freq:UInt,
        sampleRate:UInt,
        phaseWid:UInt
    ): BigInt=
    {
        // idk if i can do this without mult/div
        (BigInt(freq)*(BigInt(1)<<phaseWid))/sampleRate
    }
}
