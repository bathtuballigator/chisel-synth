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

/** Made by opus 5.5
  * Finds the positions of the `n` lowest set bits of `in`, combinationally.
  *
  * Returns `(idx, valid)`: `idx(k)` is the bit position of the k-th lowest set
  * bit, and `valid(k)` is true when at least k+1 bits are set. When `valid(k)`
  * is false, `idx(k)` is a don't-care value.
  *
  * Built as a balanced merge tree, so the depth is log2(width) merge stages.
  * Each node holds the first set bits of its range plus how many it found. A
  * merge keeps the left child's entries and fills the next slots from the
  * right child, shifted by the left count (a small one-hot mux per slot).
  * A node only carries min(n, rangeSize) entries, which keeps the lower levels
  * small.
  */
object FirstNSetBits {
  private case class Node(idx: Seq[UInt], cnt: UInt)

  def apply(in: UInt, n: Int): (Vec[UInt], Vec[Bool]) = {
    val w = in.getWidth
    require(n >= 1 && n <= w, s"n must be in 1..$w")
    val idxW = log2Ceil(w) max 1

    def merge(l: Node, r: Node): Node = {
      val nl = l.idx.length
      val nr = r.idx.length
      val m = (nl + nr) min n
      val idx = (0 until m).map { j =>
        // Slot j comes from the right child, entry (j - lc), when the left child has lc <= j entries
        val fromRight = Mux1H(
          (0 to (j min nl))
            .filter(c => j - c < nr)
            .map(c => (l.cnt === c.U) -> r.idx(j - c))
        )
        if (j < nl) Mux(l.cnt > j.U, l.idx(j), fromRight) else fromRight
      }
      val sum = l.cnt +& r.cnt
      val cnt = Mux(sum > m.U, m.U, sum)(log2Ceil(m + 1) - 1, 0)
      Node(idx, cnt)
    }

    def build(lo: Int, hi: Int): Node =
      if (hi - lo == 1) Node(Seq(lo.U(idxW.W)), in(lo).asUInt)
      else {
        val mid = (lo + hi) / 2
        merge(build(lo, mid), build(mid, hi))
      }

    val root = build(0, w)
    (VecInit(root.idx), VecInit((0 until n).map(k => root.cnt > k.U)))
  }
}
