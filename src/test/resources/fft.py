import numpy as np
import argparse
from scipy import signal
from matplotlib import pyplot as plt


def plot_fft(data, clock, filename):
    with open("src/test/resources/data.log", "r") as file:
        contents = file.read()
        data = np.array(list(map(int, contents.split(","))), dtype=np.float64)
        print(f"data: {data}, max: {data.max()}")

    data = (data - data.min()) / (data.max() - data.min())  # normalization
    print(f"testing: {data}")

    fourier = np.fft.fft(data)
    n = data.size
    timestep = 1.0 / int(args.clock)
    freq = np.fft.fftfreq(n, d=timestep)

    plt.figure(figsize=(13, 8))
    plt.plot(freq, fourier)
    plt.xlabel("Frequency (Hz)")
    plt.ylabel("Amplitude")
    plt.savefig(filename)


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
    "-c", "--clock", default=100_000_000
)  # clock frequency / sample rate
parser.add_argument("-f", "--frequency", default=440)  # note frequency for generation
parser.add_argument("-s", "--shape", default="sine")  # waveform selector


args = parser.parse_args()
print(f"clk: {args.clock}, f: {args.frequency}, s: {args.shape}")
with open("src/test/resources/data.log", "r") as file:
    contents = file.read()
    data = np.array(list(map(int, contents.split(","))), dtype=np.float64)
    print(f"data: {data}, max: {data.max()}")

t = np.linspace(0, data.size / args.clock, data.size, endpoint=False)

gen_data = gen_waveform(int(args.frequency), args.shape, t)

fourier = np.fft.rfft(gen_data)
n = data.size
timestep = 1.0 / int(args.clock)
freq = np.fft.rfftfreq(n, d=timestep)

plt.figure(figsize=(13, 8))
plt.plot(freq[:1000], np.abs(fourier)[:1000])
plt.xlabel("Frequency (Hz)")
plt.ylabel("Amplitude")
plt.savefig("fft.png")
