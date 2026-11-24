import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

// test for SquareOscillator
class SquareOscSpec extends AnyFlatSpec with ChiselScalatestTester
{
    behavior of "SquareOscillator"
    // test accumulation of phase
    it should "accumulate phase in specified increments" in{
    test(
        new SquareOscillator
        (
            amp = 100,
            sampleWid = 8,
            phaseWid = 4
        )
    ) { dut=>
        // 4 bit accumulator with increment = 1
        dut.io.increment.poke(1.U)

        // init phase is 0, amplitude is 100, so sample should be +100
        dut.io.sample.expect(100.S)
        // step 1
        dut.clock.step()
        dut.io.sample.expect(100.S)
        // step 2
        dut.clock.step()
        dut.io.sample.expect(100.S)
        // step 3
        dut.clock.step()
        dut.io.sample.expect(100.S)
        // step 4
        dut.clock.step()
        dut.io.sample.expect(100.S) 
        // step 5
        dut.clock.step()
        dut.io.sample.expect(100.S) 
        // step 6
        dut.clock.step()
        dut.io.sample.expect(100.S) 
        // step 7
        dut.clock.step()
        dut.io.sample.expect(100.S)
        // step 8, MSB becomes 1, so sample should be -100
        dut.clock.step()
        dut.io.sample.expect(-100.S)

        // pass test
        }
    }

    // test square wave shape
    it should "produce square wave using MSB of accumulator" in{

        test(
            new SquareOscillator
            (
                amp = 100,
                sampleWid = 8,
                phaseWid = 4
            )
        ) { dut =>
            // 4 bit accumulator with increment = 4
            dut.io.increment.poke(4.U)

            // init phase is 0, amplitude is 100, so sample should be +100
            dut.io.sample.expect(100.S)
            // step 1
            dut.clock.step()
            dut.io.sample.expect(100.S)
            // step 2, MSB becomes 1, so sample should be -100
            dut.clock.step()
            dut.io.sample.expect(-100.S)
            // step 3
            dut.clock.step()
            dut.io.sample.expect(-100.S)
            // step 4, overflow, MSB becomes 0, so sample should be +100
            dut.clock.step()
            dut.io.sample.expect(100.S)
            // step 5
            dut.clock.step()
            dut.io.sample.expect(100.S)
            // step 6, MSB becomes 1, so sample should be -100
            dut.clock.step()
            dut.io.sample.expect(-100.S)
            // step 7
            dut.clock.step()
            dut.io.sample.expect(-100.S)
            // step 8, overflow, MSB becomes 0, so sample should be +100
            dut.clock.step()
            dut.io.sample.expect(100.S)

            //pass test
        }
    }

    it should "wrap phase accumulator on overflow" in {
        test(
            new SquareOscillator
            (
                amp = 100,
                sampleWid = 8,
                phaseWid = 4
            )
        ) { dut =>
            // 0, 5, 10, 15, 4, 9, 14, 3, 8, 13, 2, 7, 12, 1, 6, 11, 0
            // should occassionally have 2 samples in a row with same sign and occassionally have 1
            // 4 bit accumulator with increment = 5
            dut.io.increment.poke(5.U)

            // init phase is 0, amplitude is 100, so sample should be +100
            dut.io.sample.expect(100.S)
            // step 1
            dut.clock.step() // phase=5, MSB=0
            dut.io.sample.expect(100.S)
            // step 2
            dut.clock.step() // phase=10, MSB=1
            dut.io.sample.expect(-100.S)
            // step 3
            dut.clock.step()// phase=15, MSB=1
            dut.io.sample.expect(-100.S)
            // step 4
            dut.clock.step()// phase=4, MSB=0
            dut.io.sample.expect(100.S)
            // step 5
            dut.clock.step()// phase=9, MSB=1
            dut.io.sample.expect(-100.S)
            // step 6
            dut.clock.step()// phase=14, MSB=1
            dut.io.sample.expect(-100.S)
            // step 7
            dut.clock.step()// phase=3, MSB=0
            dut.io.sample.expect(100.S)
            // step 8
            dut.clock.step()// phase=8, MSB=1
            dut.io.sample.expect(-100.S)
            // step 9
            dut.clock.step()// phase=13, MSB=1
            dut.io.sample.expect(-100.S)
            // step 10
            dut.clock.step()// phase=2, MSB=0
            dut.io.sample.expect(100.S)
            // step 11
            dut.clock.step()// phase=7, MSB=0
            dut.io.sample.expect(100.S)
            // step 12
            dut.clock.step()// phase=12, MSB=1
            dut.io.sample.expect(-100.S)
            // step 13
            dut.clock.step()// phase=1, MSB=0
            dut.io.sample.expect(100.S)
            // step 14
            dut.clock.step()// phase=6, MSB=0
            dut.io.sample.expect(100.S)
            // step 15
            dut.clock.step()// phase=11, MSB=1
            dut.io.sample.expect(-100.S)
            // step 16
            dut.clock.step()// phase=0, MSB=0
            dut.io.sample.expect(100.S)

            // pass] test
        }
    }

    it should "shorten period of square wave when increment is larger" in{
        test(
            new SquareOscillator
            (
                amp=100,
                sampleWid=8,
                phaseWid=4
            )
        ) { dut =>
            // 4 bit accumulator with increment = 8
            dut.io.increment.poke(8.U)

            // init phase is 0, amplitude is 100, so sample should be +100
            dut.io.sample.expect(100.S)
            // step 1
            dut.clock.step()// phase=8, MSB=1
            dut.io.sample.expect(-100.S)
            // step 2
            dut.clock.step()// phase=0, MSB=0
            dut.io.sample.expect(100.S)
            // step 3
            dut.clock.step()// phase=8, MSB=1
            dut.io.sample.expect(-100.S)
            // step 4
            dut.clock.step()// phase=0, MSB=0
            dut.io.sample.expect(100.S)

            // pass test
        }
    }
}