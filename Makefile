PRJ = hpipe
TARGET ?= hpipe.CPU

MILL = ./mill

TEST_DIR = build/test

TEST_TARGET ?= $(TARGET)Spec
TEST_NAME = $(lastword $(subst ., ,$(TEST_TARGET)))
TEST_TARGET_DIR = $(TEST_DIR)/$(TEST_NAME)

APP ?= dummy
APP_DIR = sim/app/$(APP)
APP_ELF = sim/app/build/$(APP)/$(APP).elf

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

verilog-sim:
	@echo Exporting SystemVerilog for Simulation...
	@$(MILL) $(PRJ).runMain $(TARGET)Sim

sim: verilog-sim
	@make -C $(APP_DIR) sim

wave: verilog-sim
	@make -C $(APP_DIR) wave

gdb-server:
	@make -C sim wave

gdb:
	@riscv64-unknown-linux-gnu-gdb --command=script/sim.gdb

init-backend:
	@make -C backend init

verilog-backend:
	@echo Exporting SystemVerilog for Backend Analysis...
	@$(MILL) $(PRJ).runMain $(TARGET)Backend

backend: verilog-backend
	@echo Analysing backend...
	@make -C backend all
	@echo 
	@echo Backend Analysis Completed
	@echo Reports available at 'backend/build'

clean:
	@make -C sim clean