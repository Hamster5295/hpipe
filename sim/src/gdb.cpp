#include "gdb.h"

#include "debug.h"
#include "emu.h"
#include "gdbstub.h"
#include "mem.h"
#include <errno.h>
#include <string.h>
#include <vector>

using namespace std;

// GDB breakpoints
vector<size_t> bps(4, -1);

gdb_action_t gdb_cont(void *args) {
  emu_set_halt(false);
  DBG("GDB cont");
  while (true) {
    if (!emu_step(1))
      break;

    if (emu_get_halt())
      break;

    if (emu_try_trap())
      return ACT_SHUTDOWN;

    if (emu_cpu()->io_sim_retire_valid) {
      for (auto it = bps.begin(); it != bps.end(); ++it) {
        if (*it == emu_cpu()->io_sim_retire_pc)
          return ACT_RESUME;
      }
    }
  }
  return ACT_RESUME;
}

gdb_action_t gdb_emu_stepi(void *args) {
  DBG("GDB emu_step");
  emu_step(1);
  if (emu_cpu()->io_sim_retire_ebreak) {
    INFO("Hit EBREAK, shutting down...");
    return ACT_SHUTDOWN;
  }
  return ACT_RESUME;
}

bool gdb_set_bp(void *args, size_t addr, bp_type_t type) {
  if (type != BP_SOFTWARE)
    return false;

  for (auto it = bps.begin(); it != bps.end(); ++it) {
    if (*it == addr)
      return false;
  }
  bps.push_back(addr);
  return true;
}

bool gdb_del_bp(void *args, size_t addr, bp_type_t type) {
  if (type != BP_SOFTWARE)
    return true;

  for (auto it = bps.begin(); it != bps.end(); ++it) {
    if (*it == addr) {
      bps.erase(it);
      break;
    }
  }
  return true;
}

void gdb_on_interrupt(void *args) { emu_set_halt(true); }

int gdb_read_mem(void *args, size_t addr, size_t len, void *val) {
  if (mem_addr_trans(addr) + len > MEM_SIZE) {
    return EFAULT;
  }
  bool unused;
  uint32_t result = mem_read(addr, &unused);
  memcpy(val, (void *)&result, len);
  return 0;
}

int gdb_write_mem(void *args, size_t addr, size_t len, void *val) {
  size_t paddr = mem_addr_trans(addr);
  if (mem_addr_trans(addr) + len > MEM_SIZE) {
    return EFAULT;
  }
  memcpy(mem_ptr() + paddr, val, len);
  return 0;
}

int gdb_read_reg(void *args, int regno, void *reg_value) {
  if (regno > 32)
    return EFAULT;

  if (regno == 32) {
    memcpy(reg_value, &emu_cpu()->io_sim_retire_pc, 4);
    return 0;
  }

  *(uint32_t *)reg_value = emu_reg(regno);

  return 0;
}

int gdb_write_reg(void *args, int regno, void *data) {
  if (regno > 32)
    return EFAULT;

  if (regno == 32) {
    memcpy(&emu_cpu()->io_sim_retire_pc, data, 4);
    return 0;
  }

  if (regno == 0) {
    return 0;
  }

  switch (regno) {
#define REG(no)                                                                \
  case (no + 1):                                                               \
    memcpy(&emu_cpu()->io_sim_regs_##no, data, 4);                             \
    break

    REGS

#undef REG
  }

  return 0;
}

size_t gdb_get_reg_bytes(int regno __attribute__((unused))) { return 4; }

target_ops gdb_get_ops() {
  return {
      .cont = gdb_cont,
      .stepi = gdb_emu_stepi,
      .get_reg_bytes = gdb_get_reg_bytes,
      .read_reg = gdb_read_reg,
      .write_reg = gdb_write_reg,
      .read_mem = gdb_read_mem,
      .write_mem = gdb_write_mem,
      .set_bp = gdb_set_bp,
      .del_bp = gdb_del_bp,
      .on_interrupt = gdb_on_interrupt,
  };
}