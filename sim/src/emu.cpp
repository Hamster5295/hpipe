#include "emu.h"
#include "VHPipe.h"
#include "debug.h"
#include "mem.h"
#include "peripheral.h"
#include <stdint.h>
#include <stdio.h>
#include <unistd.h>

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
VHPipe *cpu;
bool inited = false;

// Return Value
int ret = 0;

// Performances
int cycles = 0, branch = 0, branchMiss = 0;
uint64_t max_cycles = -1;

// GDB breakpoints
vector<size_t> bps(4, -1);
bool halted = false, shutdown = false;

void exec() {

  cpu->clock = 0;

  mem_resp_t resp;

  // Inst
  cpu->io_inst_resp_valid = mem_inst_resp(&resp);
  cpu->io_inst_resp_bits_data = resp.data;
  cpu->io_inst_resp_bits_excp = resp.excp;

  if (cpu->io_inst_resp_valid && cpu->io_inst_resp_ready)
    mem_inst_step();
  cpu->eval();

  if (cpu->io_inst_req_valid && inited) {
    cpu->io_inst_req_ready = mem_inst_req(cpu->io_inst_req_bits_addr);
  } else {
    cpu->io_inst_req_ready = false;
  }

  // Data Read
  cpu->io_read_resp_valid = mem_data_resp(&resp);
  cpu->io_read_resp_bits_data = resp.data;
  cpu->io_read_resp_bits_excp = resp.excp;

  if (cpu->io_read_resp_valid && cpu->io_read_resp_ready)
    mem_data_step();
  cpu->eval();

  if (cpu->io_read_req_valid && inited) {
    cpu->io_read_req_ready = mem_data_req(cpu->io_read_req_bits_addr);
  } else {
    cpu->io_read_req_ready = false;
  }

  // Data Write
  cpu->io_write_req_ready = 1;

  if (cpu->io_write_req_valid && inited) {
    bool excp = 0;
    mem_write(cpu->io_write_req_bits_addr, cpu->io_write_req_bits_data,
              cpu->io_write_req_bits_mask, &excp);
    // TODO: support write resp exception
  }

  peripheral_step(cpu);

  cpu->eval();
  ctx->timeInc(1);
  TRACE();

  if (cpu->io_sim_branch)
    branch++;
  if (cpu->io_sim_branchMiss)
    branchMiss++;

  cpu->clock = 1;
  cpu->eval();
  ctx->timeInc(1);
  TRACE();

  cycles++;
}

bool step(int n) {
  for (int i = 0; i < n; i++) {
    int cnt = 0;
    do {
      exec();
      if (cnt++ >= MAX_CYCLE_PER_INST) {
        ERR("No valid inst retired aftere %d cycles, stopping",
            MAX_CYCLE_PER_INST);
        return false;
      }
    } while (!cpu->io_sim_retire_valid);
    DBG("Step Retring 0x%08X", cpu->io_sim_retire_pc);
    DBG("Exec %d times", cnt);
  }

  if (cycles + n >= max_cycles) {
    INFO("Max cycles %lu reached, stopping", max_cycles);
    return false;
  }

  return true;
}

bool try_trap() {
  if (cpu->io_sim_retire_ebreak) {
    if (cpu->io_sim_regs_9) {
      ret = 1;
      ERR("Simulation FAILED at 0x%08X", cpu->io_sim_retire_pc);
    } else
      INFO(ANSI_FG_GREEN "Simulation PASSED at 0x%08X" ANSI_NONE,
           cpu->io_sim_retire_pc);
    return true;
  }
  return false;
}

void emu_run(char *file) {
  FILE *fp = fopen(file, "r");
  if (fp == NULL) {
    ERR("Image '%s' not found", file);
    ret = 1;
    return;
  }

  fseek(fp, 0L, SEEK_END);
  int size = ftell(fp);

  if (size > MEM_SIZE) {
    ERR("Image '%s' has size %d > mem size %d", file, size, MEM_SIZE);
    ret = 1;
    return;
  }

  fseek(fp, 0L, SEEK_SET);
  fread(mem_ptr(), size, 1, fp);
  INFO("Image Loaded");
  fclose(fp);

  while (true) {
    if (!step(1) || shutdown) {
      ret = 1;
      break;
    }

    if (try_trap())
      break;
  }

  INFO("Emulator Stopped");
}

gdb_action_t gdb_cont(void *args) {
  halted = false;
  DBG("GDB cont");
  while (true) {
    if (!step(1))
      break;

    if (halted)
      break;

    if (try_trap())
      return ACT_SHUTDOWN;

    if (cpu->io_sim_retire_valid) {
      for (auto it = bps.begin(); it != bps.end(); ++it) {
        if (*it == cpu->io_sim_retire_pc)
          return ACT_RESUME;
      }
    }
  }
  return ACT_RESUME;
}

gdb_action_t gdb_stepi(void *args) {
  DBG("GDB step");
  step(1);
  if (cpu->io_sim_retire_ebreak) {
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
  //   for (int i = 0; i < len; i++) {
  //     mem_write(addr + i, *(uint8_t *)val, 0x1);
  //   }
  return 0;
}

int gdb_read_reg(void *args, int regno, void *reg_value) {
  if (regno > 32)
    return EFAULT;

  if (regno == 32) {
    memcpy(reg_value, &cpu->io_sim_retire_pc, 4);
    return 0;
  }

  if (regno == 0) {
    memcpy(reg_value, &regno, 4);
    return 0;
  }

  switch (regno) {
#define REG(no)                                                                \
  case (no + 1):                                                               \
    memcpy(reg_value, &cpu->io_sim_regs_##no, 4);                              \
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
    memcpy(&cpu->io_sim_retire_pc, data, 4);
    return 0;
  }

  if (regno == 0) {
    return 0;
  }

  switch (regno) {
#define REG(no)                                                                \
  case (no + 1):                                                               \
    memcpy(&cpu->io_sim_regs_##no, data, 4);                                   \
    break

    REGS

#undef REG
  }

  return 0;
}

size_t gdb_get_reg_bytes(int regno __attribute__((unused))) { return 4; }

struct target_ops emu_init(char *fst, uint64_t cycles) {
  ctx = new VerilatedContext;
  cpu = new VHPipe(ctx);

  max_cycles = cycles;

#ifdef ENABLE_TRACE
  tfp = new VerilatedFstC;
  ctx->traceEverOn(true);
  cpu->trace(tfp, 99);

  fst = fst != NULL ? fst : (char *)"build/emu.fst";
  tfp->open(fst);
  INFO("FST waveform will be saved at '" ANSI_FG_WHITE "%s" ANSI_NONE "'", fst);
#endif

  // Reset
  cpu->reset = 0;
  exec();

  cpu->reset = 1;
  exec();

  cpu->reset = 0;
  inited = true;

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

void emu_shutdown() { shutdown = true; }

int emu_cleanup() {
  halted = true;
#ifdef ENABLE_TRACE
  ctx->timeInc(1);
  TRACE();
  tfp->flush();
  tfp->close();
#endif

  long csr_cycle =
      ((long)cpu->io_sim_csrs_cycleh << 32) | cpu->io_sim_csrs_cycle;
  long csr_instret =
      ((long)cpu->io_sim_csrs_instreth << 32) | cpu->io_sim_csrs_instret;
  INFO(ANSI_FG_WHITE "Inst per Cycle = %ld / %ld = %f" ANSI_NONE, csr_instret,
       csr_cycle, (float)csr_instret / csr_cycle);
  INFO(ANSI_FG_WHITE "Branch Miss    = %d / %d = %f" ANSI_NONE, branchMiss,
       branch, (float)branchMiss / branch);
  return ret;
}

int emu_get_cycles() { return cycles; }