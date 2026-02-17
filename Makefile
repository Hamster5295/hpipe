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

mau:
	@echo Exporting all MAUs...
	@$(MILL) $(PRJ).runMain mau.UInt4SimpleMAU
	@$(MILL) $(PRJ).runMain mau.Int4SimpleMAU
	@$(MILL) $(PRJ).runMain mau.UInt8SimpleMAU
	@$(MILL) $(PRJ).runMain mau.Int8SimpleMAU
	@$(MILL) $(PRJ).runMain mau.UInt16SimpleMAU
	@$(MILL) $(PRJ).runMain mau.Int16SimpleMAU
	@$(MILL) $(PRJ).runMain mau.Fp8E4M3SimpleMAU
	@$(MILL) $(PRJ).runMain mau.Fp8E3M4SimpleMAU
	@$(MILL) $(PRJ).runMain mau.Fp16SimpleMAU
	@$(MILL) $(PRJ).runMain mau.Bf16SimpleMAU

dpa:
	@echo Exporting all DPAs...
	@$(MILL) $(PRJ).runMain dpa.UInt4SimpleDPA
	@$(MILL) $(PRJ).runMain dpa.Int4SimpleDPA
	@$(MILL) $(PRJ).runMain dpa.UInt8SimpleDPA
	@$(MILL) $(PRJ).runMain dpa.Int8SimpleDPA
	@$(MILL) $(PRJ).runMain dpa.UInt16SimpleDPA
	@$(MILL) $(PRJ).runMain dpa.Int16SimpleDPA

dpa-fp:
	@echo Exporting float DPAs...
	@$(MILL) $(PRJ).runMain dpa.Fp8E4M3Gen3DPA
	@$(MILL) $(PRJ).runMain dpa.Fp8E3M4Gen3DPA
	@$(MILL) $(PRJ).runMain dpa.Fp16Gen3DPA
	@$(MILL) $(PRJ).runMain dpa.Bf16Gen3DPA

proposed:
	@echo Exporting proposed DPAs...
	@$(MILL) $(PRJ).runMain mau.Int8ProposedMAU
	@$(MILL) $(PRJ).runMain mau.Int16ProposedMAU
	@$(MILL) $(PRJ).runMain dpu.DPU
