import queue
import time
import mido
import serial
from pynput import keyboard

SERIAL_PORT = "COM11"   # the Nexys COM port, see Device Manager.
# WARN: wrong port will freeze the program after first input.
BAUD = 115200

VELOCITY = 100
TOGGLE_KEY = keyboard.Key.f2   # switch between MIDI in and PC keyboard
QUIT_KEY = keyboard.Key.esc

# Tracker-style piano layout: bottom row starts at Z = C4 (MIDI 60),
# top row starts at Q = C5. Black keys sit on the row above.
KEYMAP = {
    # bottom octave: Z S X D C V G B H N J M , L . ; /
    "z": 60, "s": 61, "x": 62, "d": 63, "c": 64, "v": 65, "g": 66,
    "b": 67, "h": 68, "n": 69, "j": 70, "m": 71,
    ",": 72, "l": 73, ".": 74, ";": 75, "/": 76,
    # top octave: Q 2 W 3 E R 5 T 6 Y 7 U I 9 O 0 P
    "q": 72, "2": 73, "w": 74, "3": 75, "e": 76, "r": 77, "5": 78,
    "t": 79, "6": 80, "y": 81, "7": 82, "u": 83,
    "i": 84, "9": 85, "o": 86, "0": 87, "p": 88,
}


def choose_input() -> str | None:
    try:
        names = mido.get_input_names()
    except Exception as e:   # e.g. rtmidi backend missing
        print(f"MIDI backend unavailable ({e})")
        return None
    if not names:
        return None
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


class PcKeyboard:
    """Turns global key press/release events into MIDI messages on a queue."""

    def __init__(self, out: queue.Queue, start_enabled: bool):
        self.out = out
        self.enabled = start_enabled
        self.held: dict[str, int] = {}   # key char -> note, ignores OS key repeat
        self.quit = False

    @staticmethod
    def _char(key) -> str | None:
        ch = getattr(key, "char", None)
        return ch.lower() if ch else None

    def release_all(self) -> None:
        for note in self.held.values():
            self.out.put(mido.Message("note_off", note=note, velocity=0))
        self.held.clear()

    def on_press(self, key) -> None:
        if key == TOGGLE_KEY:
            self.enabled = not self.enabled
            if not self.enabled:
                self.release_all()
            print(f"Mode: {'PC keyboard' if self.enabled else 'MIDI in'}")
            return
        if key == QUIT_KEY:
            self.quit = True
            return
        ch = self._char(key)
        if not self.enabled or ch not in KEYMAP or ch in self.held:
            return
        note = KEYMAP[ch]
        self.held[ch] = note
        self.out.put(mido.Message("note_on", note=note, velocity=VELOCITY))

    def on_release(self, key) -> None:
        ch = self._char(key)
        if ch in self.held:
            self.out.put(mido.Message("note_off", note=self.held.pop(ch), velocity=0))


def main() -> None:
    name = choose_input()
    port = None
    if name is not None:
        try:
            port = mido.open_input(name)
        except Exception as e:
            print(f"Could not open MIDI input {name!r} ({e})")
            name = None
    if name is None:
        print("No MIDI input found, PC keyboard only")
    use_pc = name is None

    print(f"Using: {name or 'PC keyboard'}  ->  {SERIAL_PORT} @ {BAUD}")
    print("Keys: Z = C4 (MIDI 60), Q = C5. "
          f"{'F2 toggles MIDI in / PC keyboard, ' if name else ''}Esc or Ctrl+C to stop")
    print(f"Mode: {'PC keyboard' if use_pc else 'MIDI in'}")

    pc_msgs: queue.Queue = queue.Queue()
    pc = PcKeyboard(pc_msgs, start_enabled=use_pc)

    with serial.Serial(SERIAL_PORT, BAUD) as ser, \
            keyboard.Listener(on_press=pc.on_press, on_release=pc.on_release):
        try:
            while not pc.quit:
                if port is not None:
                    for msg in port.iter_pending():   # non-blocking, so Ctrl+C works
                        if pc.enabled or msg.type == "sysex":
                            continue                  # drain, but don't forward
                        ser.write(bytes(msg.bytes()))
                        if msg.type != "clock":       # 24 per beat, would flood the console
                            print(msg)
                while not pc_msgs.empty():
                    msg = pc_msgs.get_nowait()
                    ser.write(bytes(msg.bytes()))
                    print(msg)
                time.sleep(0.001)
        except KeyboardInterrupt:
            pass
        finally:
            pc.release_all()
            while not pc_msgs.empty():
                ser.write(bytes(pc_msgs.get_nowait().bytes()))
            if port is not None:
                port.close()
            print("\nStopping")


if __name__ == "__main__":
    main()
