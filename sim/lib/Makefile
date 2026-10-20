LIB_DIR ?= build

libgdbstub.a:
	@echo Building $@
	@make -C mini-gdbstub
	@cp mini-gdbstub/build/libgdbstub.a $(LIB_DIR)
	@echo Done!

libriscv.so:
	@echo Building $@
	@cd spike && ./configure --prefix=$(abspath ./spike/build)
	@make -C spike $(LIB_DIR)
	@cp spike/build/lib/libriscv.so $(LIB_DIR)
	@echo Done!

all: libgdbstub.a libriscv.so