SRCS += $(shell find port -name "*.[cS]")
SRCS += $(shell find repo -maxdepth 1 -name "*.c")

INCS += port
INCS += repo

include ../Makefile

CFLAGS += -DFLAGS_STR=\"$(OPTFLAGS)\"
CFLAGS += -DPERFORMANCE_RUN=1
CFLAGS += -DITERATIONS=1