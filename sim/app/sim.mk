TARGET ?= $(notdir $(shell pwd))
BUILD_DIR = ../build
OBJ_DIR = $(BUILD_DIR)/$(TARGET)
IMAGE = $(OBJ_DIR)/$(TARGET)
$(shell mkdir -p $(OBJ_DIR))

sim: image
	make -C ../.. sim APP_DIR=$(abspath $(IMAGE)).bin

wave: image 
	make -C ../.. wave APP_DIR=$(abspath $(IMAGE)).bin

clean:
	@echo Removing build directory
	@rm -rf $(BUILD_DIR)

.DEFAULT_GOAL := image
.PHONY: image clean