import chisel3._
import chisel3.util._

object MidiUtils {
  def midiToFreq(note: Int): // 440 Hz is midi note 69
  Int = (440.0 * math.pow(2, (note - 69) / 12.0)).toInt
  def freqToMidi(freq: Double): Int =
    (69 + 12 * math.log(freq / 440.0) / math.log(2)).toInt
  def midiLookupTable(width: Int = 32): Vec[UInt] =
    VecInit((0 until 128).map { i =>
      midiToFreq(i).U(width.W)
    })
}

object DivideByConstant {
  def apply(ioVal: UInt, constantVal: BigInt): UInt = {
    require(constantVal > 0)  // we assume uint
    val wid=ioVal.getWidth

    if (constantVal.bitLength>wid)
    {
      0.U
    } else
    {
      val zeroLSBs=constantVal.bigInteger.getLowestSetBit// check number of trailing zeros in const
      val num=ioVal >> zeroLSBs // divide by 2^zeroLSBs
      val denom=constantVal >> zeroLSBs // divide by 2^zeroLSBs

      if (denom==1) // denom was a power of 2
      {
        num 
      } else 
      {
        // for non-powerof2, use constant multiplier to avoid division
        val shiftAmt=num.getWidth+denom.bitLength
        val powerTwo=(BigInt(1)<<shiftAmt) // 2^shiftAmt
        val multDenom=powerTwo+denom-1 // round up to nearest multiple of denominator
        val constMult=multDenom/denom // determin constant multiplier

        val res=constMult.U*num // multiply numerator by constant multiplier
        res >> shiftAmt // divide by 2^shiftAmt
      }


    }
  }
}

/** The first n bits that are set in a large UInt.
  * Out is a bundle of Vecs with len n. out.idx is ordered by idx(0) is first enabled
  * value. And so on.
  * The valid vec is in case not all n notes are on. Therefore it will also prioritize
  * low indices.
  */
class FirstNSetBits(n: Int, w: Int = 128) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(Vec(w, Bool())))
    val out = Decoupled(new Bundle {
      val idx   = Vec(n, UInt(log2Ceil(w).W))
      val valid = Vec(n, Bool())
    })
  })

  object S extends ChiselEnum { val idle, busy, done = Value }
  val state = RegInit(S.idle)

  val bits = Reg(UInt(w.W))
  val cnt  = Reg(UInt(log2Up(n).W))
  val idx  = Reg(Vec(n, UInt(log2Ceil(w).W)))
  val vld  = RegInit(VecInit(Seq.fill(n)(false.B)))

  // lowest set bit as one-hot: x & -x  (maps to a carry chain on FPGA)
  val oh   = bits & (~bits + 1.U)
  val rest = bits & ~oh

  io.in.ready       := state === S.idle
  io.out.valid      := state === S.done
  io.out.bits.idx   := idx
  io.out.bits.valid := vld

  switch(state) {
    is(S.idle) {
      when(io.in.fire) {
        bits := io.in.bits
        cnt  := 0.U
        vld.foreach(_ := false.B)
        state := S.busy
      }
    }
    is(S.busy) {
      when(bits.orR) {
        idx(cnt) := OHToUInt(oh)
        vld(cnt) := true.B
        bits     := rest
        cnt      := cnt + 1.U
      }
      // stop when nothing is left, or all n slots are filled
      when(!rest.orR || cnt === (n - 1).U) { state := S.done }
    }
    is(S.done) {
      when(io.out.fire) { state := S.idle }
    }
  }
}


