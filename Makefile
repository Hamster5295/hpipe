PRJ = hpipe
TARGET ?= hpipe.CPU

MILL = ./mill

TEST_DIR = build/test

TEST_TARGET ?= $(TARGET)Spec
TEST_NAME = $(lastword $(subst ., ,$(TEST_TARGET)))
TEST_TARGET_DIR = $(TEST_DIR)/$(TEST_NAME)

verilog:
	@echo Exporting SystemVerilog...
	@$(MILL) $(PRJ).runMain $(TARGET)

test-all:
	@echo Conducting all Tests..
	@$(MILL) $(PRJ).test

test:
	@echo Conducting Test for $(TEST_TARGET)
	@$(MILL) $(PRJ).test.testOnly $(TEST_TARGET) -v

vcd:
	@mkdir -p $(TEST_DIR)
	@rm -rf $(TEST_TARGET_DIR)
	@echo Conducting Test for $(TEST_TARGET) with Vcd
	@$(MILL) $(PRJ).test.testOnly $(TEST_TARGET) --verbose -- -DemitVcd=1

debug:
	@echo Exporting Debugging SystemVerilog...
	@$(MILL) $(PRJ).runMain $(TARGET)Sim

sim: debug
	@make -C sim sim

wave: debug
	@make -C sim wave

clean:
	@make -C sim clean