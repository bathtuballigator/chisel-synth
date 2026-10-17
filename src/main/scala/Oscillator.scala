import chisel3._

class SquareOscillator( // class for an oscillator that uses an accumulator to generate a square wave
    amp: Int, // the amplitude of the square wave
    sampleWid: Int, // the number of bits in the output sample
    phaseWid: Int // the number of bits in the phase accumulator
) extends Module 
{
    // require non-neg values
    require(amp>=0, "Amplitude must not be negative") // I allowed zero amplitude, which will just output a constant 0
    require(sampleWid>0, "Sample width must be positive") // number of bits
    require(phaseWid>0, "Phase width must be positive") // number of bits
    require(amp<(1<<(sampleWid-1)), "Amplitude must fit in signed sample width") // if sample width is 8 bits, max amplitude is 127, if sample width is 16 bits, max amplitude is 32767, etc.

    val io=IO(new Bundle
    {
        // chisel hardware unsigned
        val increment = Input(UInt(phaseWid.W)) // increment value for the phase accumulator, calculated from frequency and sample rate
        // neeeds to be signed
        val sample=Output(SInt(sampleWid.W)) // output sample, signed
    })
    
    // accumulator
    val acc=RegInit(0.U(phaseWid.W))
    // increment
    acc:=acc+io.increment // overflow will wrap around

    // for square wave, just use MSB
    // if MSB is 1, output -amp
    // if MSB is 0, output +amp
    // that divides the phase in half
    io.sample:=Mux(acc(phaseWid-1), (-amp).S(sampleWid.W), (+amp).S(sampleWid.W))
}


object OscConfig // calculate increment of the square wave oscillator for given frequency, sample rate, and phase width
{
    def calcIncrement(
        freq:Int, // frequency of the square wave
        sampleRate:Int, // sample rate of the DAC in Hz
        phaseWid:Int // number of bits in the phase accumulator
    ): BigInt=
    {
        require(freq>=0, "Frequency must be non-negative") // allow zero frequency, which will just output a constant value
        require(freq<sampleRate/2, "Frequency must be less than Nyquist frequency") // must be less than half the sample rate to avoid aliasing/acheive perfect fideltiy (Nyquist–Shannon sampling theorem)
        require(sampleRate>0, "Sample rate must be positive") // Hz
        require(phaseWid>0, "Phase width must be positive") // number of bits in phase accumulator
        // generated during elaboration phase, so mult/div are not on FPGA
        (BigInt(freq)*(BigInt(1)<<phaseWid))/sampleRate // increment = freq * 2^phaseWid / sampleRate
    }
}
