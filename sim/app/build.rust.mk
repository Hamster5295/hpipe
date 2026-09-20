CROSS_COMPILE := riscv64-unknown-elf-

OBJDUMP   = $(CROSS_COMPILE)objdump
OBJCOPY   = $(CROSS_COMPILE)objcopy

CARGO = cargo

include ../sim.mk
RAW_IMAGE = $(OBJ_DIR)/riscv32imc-unknown-none-elf/release/$(TARGET)

image:
	@$(CARGO) build --release --target-dir=$(OBJ_DIR)
	-@mv $(RAW_IMAGE) $(IMAGE).elf
	@$(OBJDUMP) -d $(IMAGE).elf > $(IMAGE).txt
	@echo + OBJCOPY $(IMAGE).bin
	@$(OBJCOPY) -S -O binary $(IMAGE).elf $(IMAGE).bin