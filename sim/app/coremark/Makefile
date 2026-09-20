SRCS += $(shell find port -name "*.[cS]")
SRCS += $(shell find repo -maxdepth 1 -name "*.c")

INCS += port
INCS += repo

include ../build.c.mk

CFLAGS += -DFLAGS_STR=\"$(OPTFLAGS)\"
CFLAGS += -DPERFORMANCE_RUN=1
CFLAGS += -DITERATIONS=6000