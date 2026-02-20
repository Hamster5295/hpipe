TEST := all
TEST_DIR := repo/isa/rv32ui

EXCLUDE_TEST ?= fence_i ma_data
ORIGINAL_TEST = $(basename $(notdir $(shell find $(TEST_DIR) -name "*.S" | sort)))
ALL = $(filter-out $(EXCLUDE_TEST),$(ORIGINAL_TEST))
ALL_SRCS = $(foreach d,$(TEST_DIR),$(foreach f,$(ALL),$(wildcard $(d)/$(f).S)))

RESULT = .result
$(shell touch $(RESULT))

COLOR_RED   = \033[1;31m
COLOR_GREEN = \033[1;32m
COLOR_NONE  = \033[0m

define find_src
  $(filter %/$(1).S,$(ALL_SRCS))
endef

all: $(addprefix Makefile-, $(ALL))
	@echo "test list [$(words $(ALL)) item(s)]:" $(ALL)

$(ALL): %: Makefile-%

.SECONDEXPANSION:  # this helps to call function in prerequisite
Makefile-%: $$(call find_src,%)
	@/bin/echo -e "TARGET = rv-$*\nSRCS = $<\nINC_PATH += $(shell pwd)/include \
		$(shell pwd)/repo/isa/macros/scalar \
		$(shell pwd)/repo/env\ninclude ../Makefile" > $@
	@if make -s -f $@; then \
		printf "[%8s] $(COLOR_GREEN)PASS$(COLOR_NONE)\n" $* >> $(RESULT); \
	else \
		printf "[%8s] $(COLOR_RED)FAIL$(COLOR_NONE)\n" $* >> $(RESULT); \
	fi
	-@rm -f Makefile-$*

run: all
	@cat $(RESULT)
	@grep -q "FAIL" $(RESULT); FAIL=$$?; rm $(RESULT); test $$FAIL -ne 0

clean:
	rm -rf Makefile-* build/

.PHONY: all run gdb clean $(ALL)