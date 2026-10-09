TOP = HPipe
PDK = icsprout55
PDK_DIR = /opt/pdk/icsprout55

BUILD_DIR = build
SYN_DIR = $(BUILD_DIR)/syn
STA_DIR = $(BUILD_DIR)/sta

VSRCS = $(shell find rtl -name "*.sv")
NETLIST = $(SYN_DIR)/HPipe.netlist.v

DEFAULT_GOAL := all

syn: $(NETLIST)
$(NETLIST):
	@rm -rf $(SYN_DIR)
	@mkdir -p $(SYN_DIR)
	@echo tcl script/syn.tcl $(TOP) $(PDK) $(PDK_DIR) \"$(VSRCS)\" $(NETLIST) | yosys -g -l $(SYN_DIR)/yosys.log -s -

sta: $(NETLIST)
	@rm -rf $(STA_DIR)
	@mkdir -p $(STA_DIR)
	@set -o pipefail && script/iEDA -script script/sta.tcl script/default.sdc $(NETLIST) $(TOP) $(PDK) $(STA_DIR) 2>&1 | tee $(STA_DIR)/sta.log

# iSTA why do you always create empty folders on path even if it's not your pwd?
	@rm -rf $(BUILD_DIR)/sta_*

all: syn sta

clean:
	@rm -rf build

.PHONY: syn sta clean $(NETLIST)
