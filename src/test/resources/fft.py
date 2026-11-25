import numpy as np
import argparse
from scipy import signal
from matplotlib import pyplot as plt
import sys


def do_analysis(data, filename, height):
    fourier = np.fft.rfft(data)
    n = data.size
    timestep = 1.0 / int(args.clock)
    freq = np.fft.rfftfreq(n, d=timestep)
    fourier = np.abs(fourier)[:1000]
    freq = freq[:1000]
    peaks, _ = signal.find_peaks(fourier, height=height)
    if args.graphics:
        print(peaks)
    fourier = (fourier - fourier.min()) / (
        fourier.max() - fourier.min()
    )  # normalization

    if args.graphics:
        plt.figure(figsize=(13, 8))
        plt.plot(freq, fourier)
        plt.plot(freq[peaks], fourier[peaks], "x")
        plt.xlabel("Frequency (Hz)")
        plt.ylabel("Amplitude")
        plt.savefig(filename)
        plt.close()
    return fourier


def gen_waveform(f, shape, t: np.ndarray):
    gen = 0
    match shape:
        case "square":
            gen = signal.square(2 * np.pi * f * t)
        case "sine":
            gen = np.sin(2 * np.pi * f * t)
        case "triangle":
            gen = signal.sawtooth(2 * np.pi * f * t, width=0.5)
        case "sawtooth":
            gen = signal.sawtooth(2 * np.pi * f * t)
    return gen


parser = argparse.ArgumentParser(
    prog="FFT helper script",
)

parser.add_argument(
    "-c", "--clock", default=25_000_000
)  # clock frequency / sample rate
parser.add_argument("-f", "--frequency", default=261)  # note frequency for generation
parser.add_argument("-s", "--shape", default="square")  # waveform selector
parser.add_argument("-g", "--graphics", default=False, action="store_true")


args = parser.parse_args()
if args.graphics:
    print(f"clk: {args.clock}, f: {args.frequency}, s: {args.shape}")
with open("src/test/resources/data.log", "r") as file:
    contents = file.read()
    data = np.array(list(map(int, contents.split(","))), dtype=np.float64)
    # print(f"data: {data}, max: {data.max()}")

t = np.linspace(0, int(data.size) / int(args.clock), data.size, endpoint=False)

gen_data = gen_waveform(int(args.frequency), args.shape, t)

# data = (data - data.min()) / (data.max() - data.min())  # normalization

real_fft = do_analysis(data, "fft.png", 10000)
gen_fft = do_analysis(gen_data, "gen_fft.png", 1000)

# if real_fft.size < gen_fft.size:
#     gen_fft = gen_fft[: real_fft.size]
# if gen_fft.size < real_fft.size:
#     real_fft = real_fft[: gen_fft.size]

sub = np.abs(real_fft - gen_fft)
if args.graphics:
    plt.figure(figsize=(13, 8))
    plt.plot(sub)
    plt.xlabel("Frequency (Hz)")
    plt.ylabel("Amplitude")
    plt.savefig("sub.png")
    plt.close()

sys.exit(0) if np.all(sub < 0.1) else sys.exit(1)

# fourier = np.fft.rfft(data)
# n = data.size
# timestep = 1.0 / int(args.clock)
# freq = np.fft.rfftfreq(n, d=timestep)
# fourier = np.abs(fourier)[:1000]
# freq = freq[:1000]
# peaks, _ = signal.find_peaks(fourier)
#
#
# plt.figure(figsize=(13, 8))
# plt.plot(freq, fourier)
# plt.plot(freq[peaks], fourier[peaks], "x")
# plt.xlabel("Frequency (Hz)")
# plt.ylabel("Amplitude")
# plt.savefig("fft.png")
