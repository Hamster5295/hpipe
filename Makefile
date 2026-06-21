PRJ = hpipe
TARGET ?= hpipe.HPipe

MILL = ./mill
JAVA = java

TEST_DIR = build/test

TEST_TARGET ?= $(TARGET)Spec
TEST_NAME = $(lastword $(subst ., ,$(TEST_TARGET)))
TEST_TARGET_DIR = $(TEST_DIR)/$(TEST_NAME)

BACKEND_DIR = backend


# Generate / Tests

verilog:
	@echo Exporting SystemVerilog...
	@$(MILL) $(PRJ).runMain $(TARGET) --target-dir build

test-all:
	@echo Conducting all Tests..
	@$(MILL) $(PRJ).test

test:
	@echo Conducting Test for $(TEST_TARGET)
	@$(MILL) $(PRJ).test.testOnly $(TEST_TARGET) -v

test-wave:
	@mkdir -p $(TEST_DIR)
	@rm -rf $(TEST_TARGET_DIR)
	@echo Conducting Test for $(TEST_TARGET) with Vcd
	@$(MILL) $(PRJ).test.testOnly $(TEST_TARGET) --verbose -- -DemitVcd=1

format:
	@$(MILL) _.reformat

lint:
	@$(JAVA) -jar src/main/resources/scalastyle -c .scalastyle.xml src


# Simulation with Verilator

SIM_DIR = sim

APP ?= dummy
APP_DIR = $(SIM_DIR)/app/$(APP)
APP_ELF = $(SIM_DIR)/app/build/$(APP)/$(APP).elf


verilog-sim:
	@echo Exporting SystemVerilog for Simulation...
	@$(MILL) $(PRJ).runMain $(TARGET)Debug --target-dir sim/rtl

sim: verilog-sim
	@$(MAKE) -C $(APP_DIR) sim

wave: verilog-sim
	@$(MAKE) -C $(APP_DIR) wave

header: verilog-sim
	@$(MAKE) -C $(SIM_DIR) header

gdb-server:
	@$(MAKE) -C $(SIM_DIR) wave

gdb:
	@$(MAKE) -C $(APP_DIR) image
	@riscv64-unknown-elf-gdb --command=script/sim.gdb sim/app/build/$(APP)/$(APP).elf


# FPGA Analysis

FPGA_DIR = $(BACKEND_DIR)/fpga

verilog-fpga:
	@echo Exporting SystemVerilog for FPGA Analysis...
	@$(MILL) $(PRJ).runMain $(TARGET)Fpga --target-dir $(FPGA_DIR)/rtl

init-fpga: verilog-fpga
	@$(MAKE) -C $(FPGA_DIR) init

fpga: verilog-fpga
	@$(MAKE) -C $(FPGA_DIR) run


# ASIC Analysis

ASIC_DIR = $(BACKEND_DIR)/asic

init-asic:
	@$(MAKE) -C $(ASIC_DIR) init

verilog-asic:
	@echo Exporting SystemVerilog for ASIC Analysis...
	@$(MILL) $(PRJ).runMain $(TARGET)Asic --target-dir $(ASIC_DIR)/rtl

asic: verilog-asic
	@echo Analysing backend...
	@$(MAKE) -C $(ASIC_DIR) all
	@echo 
	@echo Backend Analysis Completed
	@echo Reports available at '$(ASIC_DIR)/build'


# Clean up

clean:
	@$(MAKE) -C sim clean
	@rm -rf build