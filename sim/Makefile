include lib/Makefile

TOP = Top

VERILATOR := verilator

BUILD_DIR = build
OBJ_DIR = $(BUILD_DIR)/obj/sim
LIB_DIR = $(BUILD_DIR)/obj
BIN = $(BUILD_DIR)/emu

$(shell mkdir -p $(OBJ_DIR))

ifdef APP_DIR
SIMFLAGS += -b$(APP_DIR)
SIMFLAGS += -t$(basename $(APP_DIR)).fst
endif

LIB = lib
LIBS = $(addprefix $(LIB_DIR)/, $(LIB_LIST))

# CFLAGS += $(addprefix -I, $(abspath include)) 
CFLAGS += $(addprefix -I, $(abspath $(OBJ_DIR))) 
CFLAGS += $(addprefix -I, $(abspath include)) 
CFLAGS += $(addprefix -I, $(abspath lib/mini-gdbstub/include)) 
CFLAGS += -s -O3
CFLAGS += -Wno-write-strings

VFLAGS += --no-timing		# handle #x delays 

# LDFLAGS += $(addprefix -L, /usr/lib)
LDFLAGS += $(addprefix -L, $(abspath $(LIB_DIR)))
LDFLAGS += $(addprefix -l, "gdbstub")

CSRCS = $(shell find -L src -name "*.c") 
CXXSRCS = $(shell find -L src -name "*.cpp")
VSRCS = $(shell find -L rtl -name "*.sv")
SRCS = $(VSRCS) $(CSRCS) $(CXXSRCS)	

$(LIBS): 
	@make -C $(LIB) $(lastword $(subst /, ,$@))
	@mv $(LIB)/$(lastword $(subst /, ,$@)) $@

build: $(LIBS)
	-@rm $(BIN)
	@verilator --cc --build -j 0  \
                                $(SRCS) \
                                $(VFLAGS) \
                                $(addprefix -LDFLAGS , $(LDFLAGS)) \
                                $(addprefix -CFLAGS , $(CFLAGS)) \
                                --top $(TOP) \
                                --Mdir $(OBJ_DIR) \
                                --exe -o $(abspath $(BIN))
	

sim: build
	@$(BIN) $(SIMFLAGS)

wave: OBJ_DIR = $(BUILD_DIR)/obj/wave
wave: VFLAGS += --trace-fst
wave: CFLAGS += $(addprefix -D,ENABLE_TRACE)
wave: sim

clean:
	@rm -rf $(BUILD_DIR)
	@rm -rf app/$(BUILD_DIR)

.PHONY: build clean sim wave