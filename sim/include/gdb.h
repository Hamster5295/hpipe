#ifndef __GDB_H
#define __GDB_H

extern "C" {
#include "gdbstub.h"
}

target_ops gdb_get_ops();

#endif // __GDB_H