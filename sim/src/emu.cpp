#include "emu.h"
#include "VTop.h"
#include "debug.h"
#include <cstdint>
#include <stdint.h>
#include <stdio.h>
#include <vector>

extern "C" {
#include "gdbstub.h"
}

using namespace std;

#ifdef ENABLE_TRACE

#include "verilated_fst_c.h"

VerilatedFstC *tfp;
#define TRACE()                                                                \
  do {                                                                         \
    tfp->dump(ctx->time());                                                    \
  } while (0)

#else

#define TRACE()

#endif

// Internal states
VerilatedContext *ctx;
VTop *cpu;

vector<size_t> bps(4, -1);
bool halted = false;

uint8_t mem[MEM_SIZE];

int ret = 0;

uint32_t mem_addr_trans(uint32_t addr) { return addr - 0x80000000; }

uint32_t mem_read(uint32_t addr) {
  uint32_t paddr = mem_addr_trans(addr);
  if (paddr >= MEM_SIZE) {
    ERR("Invalid memory read at 0x%08X", addr);
    return 0;
  }

  uint32_t result = mem[paddr + 3] << 24 | mem[paddr + 2] << 16 |
                    mem[paddr + 1] << 8 | mem[paddr];

  DBG("[Mem] R [0x%08X] -> 0x%08X", addr, result);

  return result;
}

void mem_write(uint32_t addr, uint32_t data, uint32_t mask) {
  uint32_t paddr = mem_addr_trans(addr);

  if (paddr >= MEM_SIZE) {
    ERR("Invalid memory write at 0x%08X", addr);
    return;
  }

  DBG("[Mem] W [0x%08X] <- 0x%08X", addr, data);

  for (int i = 0; i < 4; i++) {
    if ((mask >> i) & 0x1)
      mem[paddr + i] = 0xFF & (data >> i * 8);
  }
}

void exec() {

  cpu->clock = 0;

  if (cpu->io_instFetch_addr >= 0x80000000)
    cpu->io_instFetch_inst = mem_read(cpu->io_instFetch_addr);

  if (cpu->io_memLoad_req)
    cpu->io_memLoad_data = mem_read(cpu->io_memLoad_addr);

  if (cpu->io_memStore_req)
    mem_write(cpu->io_memStore_addr, cpu->io_memStore_data,
              cpu->io_memStore_mask);

  cpu->eval();
  ctx->timeInc(1);
  TRACE();

  cpu->clock = 1;
  cpu->eval();
  ctx->timeInc(1);
  TRACE();
}

bool step(int n) {
  for (int i = 0; i < n; i++) {
    int cnt = 0;
    do {
      exec();
      DBG("Step if 0x%08X", cpu->io_debug_pcIf);
      if (cnt++ >= MAX_CYCLE_PER_INST) {
        WARN("No valid inst retired aftere %d cycles, stopping",
             MAX_CYCLE_PER_INST);
        return false;
      }
    } while (!cpu->io_retire_valid);
    DBG("Exec %d times", cnt);
  }
  return true;
}

bool try_trap() {
  if (cpu->io_retire_ebreak) {
    if (cpu->io_debug_regs_9) {
      ret = 1;
      INFO("Hit " ANSI_FG_RED "BAD" ANSI_NONE " Trap at 0x%08X",
           cpu->io_retire_pc);
    } else
      INFO("Hit " ANSI_FG_GREEN "GOOD" ANSI_NONE " Trap at 0x%08X",
           cpu->io_retire_pc);
    return true;
  }
  return false;
}

gdb_action_t gdb_cont(void *args) {
  halted = false;
  DBG("GDB cont");
  while (!halted) {
    if (!step(1))
      break;

    if (try_trap())
      return ACT_SHUTDOWN;

    if (cpu->io_retire_valid) {
      for (auto it = bps.begin(); it != bps.end(); ++it) {
        if (*it == cpu->io_retire_pc)
          return ACT_RESUME;
      }
    }
  }
  return ACT_RESUME;
}

gdb_action_t gdb_stepi(void *args) {
  DBG("GDB step");
  step(1);
  if (cpu->io_retire_ebreak) {
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

void gdb_on_interrupt(void *args) { halted = true; }

int gdb_read_mem(void *args, size_t addr, size_t len, void *val) {
  if (mem_addr_trans(addr) + len > MEM_SIZE) {
    return EFAULT;
  }
  uint32_t result = mem_read(addr);
  memcpy(val, (void *)&result, len);
  return 0;
}

int gdb_write_mem(void *args, size_t addr, size_t len, void *val) {
  size_t paddr = mem_addr_trans(addr);
  if (mem_addr_trans(addr) + len > MEM_SIZE) {
    return EFAULT;
  }
  memcpy(mem + paddr, val, len);
  //   for (int i = 0; i < len; i++) {
  //     mem_write(addr + i, *(uint8_t *)val, 0x1);
  //   }
  return 0;
}

int gdb_read_reg(void *args, int regno, void *reg_value) {
  if (regno > 32)
    return EFAULT;

  if (regno == 32) {
    memcpy(reg_value, &cpu->io_retire_pc, 4);
    return 0;
  }

  if (regno == 0) {
    memcpy(reg_value, &regno, 4);
    return 0;
  }

  switch (regno) {
#define REG(no)                                                                \
  case (no + 1):                                                               \
    memcpy(reg_value, &cpu->io_debug_regs_##no, 4);                            \
    break

    REGS

#undef REG
  }

  return 0;
}

int gdb_write_reg(void *args, int regno, void *data) {
  if (regno > 32)
    return EFAULT;

  if (regno == 32) {
    memcpy(&cpu->io_retire_pc, data, 4);
    return 0;
  }

  if (regno == 0) {
    return 0;
  }

  switch (regno) {
#define REG(no)                                                                \
  case (no + 1):                                                               \
    memcpy(&cpu->io_debug_regs_##no, data, 4);                                 \
    break

    REGS

#undef REG
  }

  return 0;
}

size_t gdb_get_reg_bytes(int regno __attribute__((unused))) { return 4; }

struct target_ops emu_init() {
  ctx = new VerilatedContext;
  cpu = new VTop(ctx);

#ifdef ENABLE_TRACE
  tfp = new VerilatedFstC;
  ctx->traceEverOn(true);
  cpu->trace(tfp, 99);
  tfp->open("build/top.fst");
#endif

  // Reset
  cpu->reset = 0;
  exec();

  cpu->reset = 1;
  exec();

  cpu->reset = 0;

  return {
      .cont = gdb_cont,
      .stepi = gdb_stepi,
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

int emu_cleanup() {
#ifdef ENABLE_TRACE
  ctx->timeInc(1);
  TRACE();
  tfp->flush();
  tfp->close();
#endif
  return ret;
}