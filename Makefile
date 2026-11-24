JAVAC    := javac
JAVA     := java
JFLAGS   := -encoding UTF-8 -Xlint:all -d $(OUT)

OUT      := out
SRC_DIR  := src
TEST_DIR := tests
MAIN     := Main
TESTS    := ConfigToTomlTest

SRC      := $(wildcard $(SRC_DIR)/*.java)
TEST := $(wildcard tests/*.java)

IN       ?= examples/web-server.cfg
OUTFILE  ?= web-server.toml

all: build

build:
	@mkdir -p $(OUT)
	$(JAVAC) $(JFLAGS) $(SRC) $(TEST)

test: build
	$(JAVA) -cp $(OUT) $(TESTS)

run: build
	$(JAVA) -cp $(OUT) $(MAIN) -i $(IN) -o $(OUTFILE)

clean:
	rm -rf $(OUT)

.PHONY: all build test run clean