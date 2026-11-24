
all:
	sbt "runMain Nexys4Gen"

test:
	sbt test

activate:
	echo .venv/Scripts/activate.ps1
# assumes activated venv

midi:
	python scripts/midi_forward.py

.PHONY: all test activate
