CROSS_COMPILE := riscv64-unknown-elf-

AS        = $(CROSS_COMPILE)gcc
CC        = $(CROSS_COMPILE)gcc
CXX       = $(CROSS_COMPILE)g++
LD        = $(CROSS_COMPILE)ld
AR        = $(CROSS_COMPILE)ar
OBJDUMP   = $(CROSS_COMPILE)objdump
OBJCOPY   = $(CROSS_COMPILE)objcopy
READELF   = $(CROSS_COMPILE)readelf

TARGET ?= $(notdir $(shell pwd))
BUILD_DIR = ../build
OBJ_DIR = $(BUILD_DIR)/$(TARGET)
IMAGE = $(OBJ_DIR)/$(TARGET)
$(shell mkdir -p $(OBJ_DIR))

SRCS += $(shell find ../common -name "*.[cS]")
# SRCS += $(shell find ../common/ -name "*.c")

OBJS      = $(addprefix $(OBJ_DIR)/, $(addsuffix .o, $(basename $(SRCS))))
LINKAGE   = $(OBJS)
LDSCRIPTS += ../linker.ld

INC_PATH += include ../common/include
INCFLAGS += $(addprefix -I, $(INC_PATH))

# General
CFLAGS   += -O2 -MMD -Wall -Werror -g $(INCFLAGS) \
            -fno-asynchronous-unwind-tables -fno-builtin -fno-stack-protector \
            -Wno-main -U_FORTIFY_SOURCE -fvisibility=hidden
CXXFLAGS +=  $(CFLAGS) -ffreestanding -fno-rtti -fno-exceptions
ASFLAGS  += -MMD $(INCFLAGS)
LDFLAGS  += -z noexecstack $(addprefix -T, $(LDSCRIPTS)) --gc-sections

# RV Specific
COMMON_CFLAGS := -fno-pic -mcmodel=medany -mstrict-align -march=rv32im_zicsr -mabi=ilp32
CFLAGS        += $(COMMON_CFLAGS) -static
ASFLAGS       += $(COMMON_CFLAGS) -O0
LDFLAGS       += -melf32lriscv -nostdlib -L/opt/riscv/lib/gcc/riscv64-unknown-elf/15.1.0/rv32im/ilp32

# Emulator Specific
CFLAGS    += -fdata-sections -ffunction-sections
LDFLAGS   += --defsym=_pmem_start=0x80000000 --defsym=_entry_offset=0x0
LDFLAGS   += --gc-sections 

### Rule (compile): a single `.c` -> `.o` (gcc)
$(OBJ_DIR)/%.o: %.c
	@mkdir -p $(dir $@) && echo + CC $<
	@$(CC) -std=gnu11 $(CFLAGS) -c -o $@ $(realpath $<)

### Rule (compile): a single `.cc` -> `.o` (g++)
$(OBJ_DIR)/%.o: %.cc
	@mkdir -p $(dir $@) && echo + CXX $<
	@$(CXX) -std=c++17 $(CXXFLAGS) -c -o $@ $(realpath $<)

### Rule (compile): a single `.cpp` -> `.o` (g++)
$(OBJ_DIR)/%.o: %.cpp
	@mkdir -p $(dir $@) && echo + CXX $<
	@$(CXX) -std=c++17 $(CXXFLAGS) -c -o $@ $(realpath $<)

### Rule (compile): a single `.S` -> `.o` (gcc, which preprocesses and calls as)
$(OBJ_DIR)/%.o: %.S
	@mkdir -p $(dir $@) && echo + AS $<
	@$(AS) $(ASFLAGS) -c -o $@ $(realpath $<)

### Rule (link): objects (`*.o`) and libraries (`*.a`) -> `IMAGE.elf`, the final ELF binary to be packed into image (ld)
$(IMAGE).elf: $(LINKAGE) $(LDSCRIPTS)
	@echo + LD $(IMAGE).elf
	@$(LD) $(LDFLAGS) -o $@ --start-group $(LINKAGE) -lgcc --end-group -M=$(IMAGE).map
	@$(OBJDUMP) -d $(IMAGE).elf > $(IMAGE).txt
	@echo + OBJCOPY $(IMAGE).bin
	@$(OBJCOPY) -S -O binary $(IMAGE).elf $(IMAGE).bin

image:: $(IMAGE).elf

clean:
	@echo Removing build directory
	@rm -rf $(BUILD_DIR)

.DEFAULT_GOAL := image
.PHONY: image clean