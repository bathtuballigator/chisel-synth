import sys
import time
import mido
import serial

SERIAL_PORT = "COM11"   # the Nexys COM port, see Device Manager
BAUD = 115200


def choose_input() -> str:
    names = mido.get_input_names()
    if not names:
        sys.exit("No MIDI input found")
    if len(names) == 1:
        return names[0]

    print("Multiple MIDI inputs found:")
    for i, name in enumerate(names):
        print(f"  {i}: {name}")
    while True:
        try:
            idx = int(input("Choose an index: "))
            if 0 <= idx < len(names):
                return names[idx]
        except ValueError:
            pass
        print(f"Enter a number between 0 and {len(names) - 1}")


def main() -> None:
    name = choose_input()
    print(f"Using: {name}  ->  {SERIAL_PORT} @ {BAUD}  (Ctrl+C to stop)")

    with serial.Serial(SERIAL_PORT, BAUD) as ser, mido.open_input(name) as port:
        prev_msg = None
        try:
            while True:
                for msg in port.iter_pending():   # non-blocking, so Ctrl+C works
                    print(msg)
                    if msg.type == "sysex":
                        continue
                    ser.write(bytes(msg.bytes()))
                    if msg.type == "clock":       # 24 per beat, would flood the console
                        continue
                    if prev_msg is not None and msg != prev_msg:
                        print(msg)
                        prev_msg = msg
                time.sleep(0.001)
        except KeyboardInterrupt:
            print("\nStopping")


if __name__ == "__main__":
    main()
