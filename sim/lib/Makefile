LIB_LIST += libgdbstub.a

INC += $(abspath mini-gdbstub/include)

libgdbstub.a:
	@echo Building $@
	@make -C mini-gdbstub
	@cp mini-gdbstub/build/libgdbstub.a .
	@echo Done!