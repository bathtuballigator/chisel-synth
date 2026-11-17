import chisel3._
import chisel3.experimental.Analog
import chisel3.experimental.attach
import chisel3.util._

class SynthTopIO extends Bundle {
  val out = Output(Bool())
  val rx = Input(Bool()) // midi rx
  val heldBin = Output(UInt(7.W))
}

/** Top module for the Synth. polyphonyCount synths are generated for each voice
  * type.
  *
  * A state machine handles
  *   - Send midi signal to handler
  *   - Let handler calculate the first n set notes
  *   - Send equivalent frequency to Synth generators
  *
  * n is polyphony count.
  *
  * NOTE:
  *   - This plays all unique voices at once when a note is held
  *   - The polyphony is then the multiple frequencies overlapped as well as all
  *     the diff voices.
  *
  * @param clockFrequency
  */
class SynthTop(
    clockFrequency: Int = Nexys4.clockFrequency
) extends Module {
  val io = IO(new SynthTopIO())

  // Setup helper modules
  val midi = Module(
    new MidiTop(clkHz = clockFrequency, baud = Midi.midiBaudRate)
  )
  midi.io.rx := io.rx

  io.heldBin := midi.io.data1

  val sampleWid = 8
  val voiceCount = Env.amntSynths

  val pwm = Module(
    new PWMGenerator(
      sampleWid = sampleWid,
      numVoices = voiceCount
    )
  )
  val midiToFreqLut = MidiUtils.midiLookupTable(width = 32)

  // Handler for calculating first n set bits of midi
  val firstNSetBits = Module(
    new FirstNSetBits(n = Env.polyphonyCount, w = midi.io.held.getWidth)
  )

  val synths: Seq[Seq[Synth]] = for {
    i <- 0 until Env.polyphonyCount
  } yield {

    // waveform is voice type. bank is index of voice type in voiceList
    for ((waveform, bank) <- Env.voiceList.zipWithIndex) yield {
      val idx = bank * Env.polyphonyCount + i // one dimensional index

      val synth = Module(
        new Synth( // TODO: Add decoupled to io
          sampleWid = sampleWid,
          clockFrequency = clockFrequency,
          voice = waveform
        )
      )
      pwm.io.samples(idx) := synth.io.sample
      synth
    }
  }

  // FSM
  //// Setup states for FSM
  object S extends ChiselEnum { val enqMidi, waitDeq = Value }
  val state = RegInit(S.enqMidi)

  // Creates a reg with Bundle type of firstNSetBits.out.bits with zero-filled Vecs.
  val nSetBits = RegInit(0.U.asTypeOf(chiselTypeOf(firstNSetBits.io.out.bits)))

  //// Set defaults
  firstNSetBits.io.in.noenq()
  firstNSetBits.io.out.nodeq()
  for ((midiVal, idx) <- nSetBits.idx.zipWithIndex) {
    val isIdxValid = nSetBits.valid(idx)
    for (synth <- synths(idx)) { // have all different voices play at once
      synth.io.frequencyHz := Mux(
        isIdxValid,
        midiToFreqLut(midiVal),
        0.U
      )
    }
  }

  switch(state) {
    is(S.enqMidi) {
      firstNSetBits.io.in.enq(midi.io.held)
      when(firstNSetBits.io.in.fire) {
        state := S.waitDeq
      }
    }
    is(S.waitDeq) {
      when(firstNSetBits.io.out.valid) {
        nSetBits := firstNSetBits.io.out.deq()
        state := S.enqMidi
      }
    }
  }

  pwm.io.PWM_en := midi.io.held.orR // enable pwm if any note is held

  io.out := pwm.io.PWM_out
}
