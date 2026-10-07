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

// test for OscConfig
class OscConfigSpec extends AnyFlatSpec // no hardware instantiated, so no ChiselScalatestTester
{
    behavior of "OscConfig"
    it should "calculate correct phase increment for given frequency" in{
            val sampleRate = 48000 // 48kHz
            val phaseWidth = 16 // 16 bit phase accumulator
            val frequency = 440 // 440Hz

            val expected=BigInt(frequency) * (BigInt(1)<<phaseWidth) / sampleRate

            val actual = OscConfig.calcIncrement(
                freq=frequency,
                sampleRate=sampleRate,
                phaseWid=phaseWidth
            )

            assert(actual==expected)
        }

    it should "return 0 for frequencies smaller than sample rate / 2^phaseWidth" in{
        val sampleRate = 48000 // 48kHz
        val phaseWidth = 8 // 8 bit phase accumulator
        val frequency = 1 // 1Hz

        val expected=BigInt(0)

        val actual = OscConfig.calcIncrement(
            freq=frequency,
            sampleRate=sampleRate,
            phaseWid=phaseWidth
        )

        assert(actual==expected)
    }

    it should "reject negative frequencies" in{
        val sampleRate = 48000 // 48kHz
        val phaseWidth = 16 // 16 bit phase accumulator
        val frequency = -440 // -440Hz

        assertThrows[IllegalArgumentException] {
            OscConfig.calcIncrement(
                freq=frequency,
                sampleRate=sampleRate,
                phaseWid=phaseWidth
            )
        }
    }

    it should "reject frequencies larger than Nyquist frequency" in{
        val sampleRate = 48000 // 48kHz
        val phaseWidth = 16 // 16 bit phase accumulator
        val frequency = 48000 // 48kHz

        assertThrows[IllegalArgumentException] {
            OscConfig.calcIncrement(
                freq=frequency,
                sampleRate=sampleRate,
                phaseWid=phaseWidth
            )
        }
    }

    it should "reject negative sample rates" in{
        val sampleRate = -48000 // -48kHz
        val phaseWidth = 16 // 16 bit phase accumulator
        val frequency = 440 // 440Hz

        assertThrows[IllegalArgumentException] {
            OscConfig.calcIncrement(
                freq=frequency,
                sampleRate=sampleRate,
                phaseWid=phaseWidth
            )
        }
    }

    it should "reject negative phase widths" in{
        val sampleRate = 48000 // 48kHz
        val phaseWidth = -16 // -16 bit phase accumulator
        val frequency = 440 // 440Hz

        assertThrows[IllegalArgumentException] {
            OscConfig.calcIncrement(
                freq=frequency,
                sampleRate=sampleRate,
                phaseWid=phaseWidth
            )
        }
    }

    it should "reject 0 phase widths" in{
        val sampleRate = 48000 // 48kHz
        val phaseWidth = 0 // 0 bit phase accumulator
        val frequency = 440 // 440Hz

        assertThrows[IllegalArgumentException] {
            OscConfig.calcIncrement(
                freq=frequency,
                sampleRate=sampleRate,
                phaseWid=phaseWidth
            )
        }
    }

    it should "reject 0 sample rates" in{
        val sampleRate = 0 // 0Hz
        val phaseWidth = 16 // 16 bit phase accumulator
        val frequency = 440 // 440Hz

        assertThrows[IllegalArgumentException] {
            OscConfig.calcIncrement(
                freq=frequency,
                sampleRate=sampleRate,
                phaseWid=phaseWidth
            )
        }
    }

    it should "accept 0 frequency" in{
        val sampleRate = 48000 // 48kHz
        val phaseWidth = 16 // 16 bit phase accumulator
        val frequency = 0 // 0Hz

        val expected=BigInt(0)

        val actual = OscConfig.calcIncrement(
            freq=frequency,
            sampleRate=sampleRate,
            phaseWid=phaseWidth
        )

        assert(actual==expected)
    }
    
}
