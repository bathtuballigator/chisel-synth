import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class PWMSpec extends AnyFlatSpec with ChiselScalatestTester {
  "PWM" should "switch on and off at the right time" in test(new PWM(4) {
    dut =>
    dut.io.PWM_in.poke(2.U)
    dut.io.PWM_out.expect(false.B)
    dut.clock.step()
    dut.io.PWM_out.expect(false.B)
    dut.clock.step()
    dut.io.PWM_out.expect(true.B)
    dut.clock.step()
    dut.io.PWM_out.expect(true.B)
    dut.clock.step()
    dut.io.PWM_out.expect(false.B)
    dut.clock.step()
  })

  it should "only switch target at accumulator zero" in test(new PWM(4) {
    dut =>
    dut.io.PWM_in.poke(2.U)
    dut.io.PWM_out.expect(false.B)
    dut.clock.step()
    dut.io.PWM_out.expect(false.B)
    dut.clock.step()
    dut.io.PWM_out.expect(true.B)
    dut.io.PWM_in.poke(1.U)
    dut.clock.step()
    dut.io.PWM_out.expect(true.B)
    dut.clock.step()
    dut.io.PWM_out.expect(false.B)
    dut.clock.step()
    dut.io.PWM_out.expect(true.B)
    dut.clock.step()

  })

  it should "accept non powers of two for accumulator size" in test(new PWM(5) {
    dut =>
    dut.io.PWM_in.poke(3.U)
    dut.io.PWM_out.expect(false.B)
    dut.clock.step()
    dut.io.PWM_out.expect(false.B)
    dut.clock.step()
    dut.io.PWM_out.expect(false.B)
    dut.clock.step()
    dut.io.PWM_out.expect(true.B)
    dut.clock.step()
    dut.io.PWM_out.expect(true.B)
    dut.clock.step()
    dut.io.PWM_out.expect(false.B)
    dut.clock.step()
    dut.io.PWM_out.expect(false.B)
  })

  it should "be off for PWM_in cycles, then on for counter_size-PWM_in cycles" in test(
    new PWM(3) {
      dut =>
      // off for 0/3 cycle

      dut.io.PWM_in.poke(0.U)
      dut.io.PWM_out.expect(true.B)
      dut.clock.step()
      dut.io.PWM_out.expect(true.B)
      dut.clock.step()
      dut.io.PWM_out.expect(true.B)

      // off for 1/3 cycle
      dut.io.PWM_in.poke(1.U)
      dut.clock.step()
      dut.io.PWM_out.expect(false.B)
      dut.clock.step()
      dut.io.PWM_out.expect(true.B)
      dut.clock.step()
      dut.io.PWM_out.expect(true.B)
      dut.clock.step()

      dut.io.PWM_out.expect(false.B) // restart counter
      dut.clock.step()
      dut.io.PWM_out.expect(true.B)
      dut.clock.step()
      dut.io.PWM_out.expect(true.B)

      // off for 2/3 cycle
      dut.io.PWM_in.poke(2.U)
      dut.clock.step()
      dut.io.PWM_out.expect(false.B)
      dut.clock.step()
      dut.io.PWM_out.expect(false.B)
      dut.clock.step()
      dut.io.PWM_out.expect(true.B)

      // off for 2/3 cycle
      dut.clock.step()
      dut.io.PWM_in.poke(3.U)
      dut.io.PWM_out.expect(false.B)
      dut.clock.step()
      dut.io.PWM_out.expect(false.B)
      dut.clock.step()
      dut.io.PWM_out.expect(false.B)
      dut.clock.step()
      dut.io.PWM_out.expect(false.B)
    }
  )
}
