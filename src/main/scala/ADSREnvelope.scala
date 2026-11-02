import chisel3._
import chisel3.util._

class ADSREnvelope(
    attackMs: Int,
    decayMs: Int,
    sustain: Double,
    releaseMs: Int,
    clockHz: Int = Nexys4.clockFrequency,
    levelWid: Int = 16,
    tickHz: Int = 1000
) extends Module {
  require(attackMs >= 0 && decayMs >= 0 && releaseMs >= 0, "times must not be negative")
  require(sustain >= 0.0 && sustain <= 1.0, "sustain must be between 0 and 1")
  require(tickHz > 0 && clockHz >= tickHz, "tick must not be faster than the clock")

  val io = IO(new Bundle {
    val gate   = Input(Bool())
    val level  = Output(UInt(levelWid.W))
    val active = Output(Bool())
  })

  val maxInt = (1 << levelWid) - 1
  def rate(ms: Int): UInt = {
    val perTick = if (ms == 0) maxInt.toDouble else maxInt / (ms / 1000.0 * tickHz)
    math.min(maxInt, math.max(1, math.round(perTick).toInt)).U(levelWid.W)
  }
  val attackRate  = rate(attackMs)
  val decayRate   = rate(decayMs)
  val releaseRate = rate(releaseMs)
  val sustainLvl  = math.round(sustain * maxInt).toInt.U(levelWid.W)
  val maxLvl      = maxInt.U(levelWid.W)

  val tickPeriod = clockHz / tickHz
  val tick = if (tickPeriod == 1) true.B else Counter(true.B, tickPeriod)._2

  object State extends ChiselEnum {
    val idle, attack, decay, sustainSt, release = Value
  }
  import State._

  val state = RegInit(idle)
  val level = RegInit(0.U(levelWid.W))

  when(io.gate && (state === idle || state === release)) {
    state := attack
  }.elsewhen(!io.gate && (state === attack || state === decay || state === sustainSt)) {
    state := release
  }.elsewhen(tick) {
    switch(state) {
      is(attack) {
        when(maxLvl - level <= attackRate) {
          level := maxLvl
          state := decay
        }.otherwise {
          level := level + attackRate
        }
      }
      is(decay) {
        when(level <= sustainLvl +& decayRate) {
          level := sustainLvl
          state := sustainSt
        }.otherwise {
          level := level - decayRate
        }
      }
      is(sustainSt) {
        level := sustainLvl
      }
      is(release) {
        when(level <= releaseRate) {
          level := 0.U
          state := idle
        }.otherwise {
          level := level - releaseRate
        }
      }
    }
  }

  io.level  := level
  io.active := state =/= idle
}