#include "emu.h"
#include "debug.h"
#include "gdb.h"
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

bool emu_step(int n) { return step(n); }

bool emu_try_trap() { return try_trap(); }

bool emu_get_halt() { return halted; }
void emu_set_halt(bool halt) { halted = halt; }

VHPipe *emu_get_cpu() { return cpu; }

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

  return gdb_get_ops();
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