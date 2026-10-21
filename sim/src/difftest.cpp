#include "difftest.h"
#include "debug.h"
#include "emu.h"
#include "mem.h"
#include "peripheral.h"
#include "riscv/processor.h"
#include "riscv/sim.h"
#include "utils.h"
#include <cstdlib>

using namespace std;

sim_t *model;
cfg_t cfg;

auto *mem_ref = new mem_t(MEM_SIZE);
vector<pair<reg_t, abstract_mem_t *>> mems{{RESET_VECTOR, mem_ref},
                                           {UART_BASE, new mem_t(4096)}};

bool enabled = false;

bool diff_enabled() { return enabled; }

void diff_init() {
  enabled = true;

  cfg.isa = "RV32IMC_zicsr_zicntr";
  cfg.priv = "M";
  cfg.mem_layout = {mem_cfg_t(RESET_VECTOR, MEM_SIZE),
                    mem_cfg_t(UART_BASE, 4096)};
  cfg.hartids = {0};
  cfg.start_pc.set_global(RESET_VECTOR);
  cfg.pmpregions = 0;

  model = new sim_t(&cfg, false, mems, {}, false, {"none"}, {}, nullptr, false,
                    nullptr, false, nullptr, nullopt);
  model->get_core(0)->reset();
  model->get_core(0)->get_state()->pc = RESET_VECTOR;
  INFO("Difftest Inited");
}

void diff_cleanup() {
  delete model;
  INFO("Difftest Cleaned Up");
}

void diff_mem_write(uint32_t addr, uint8_t *data, uint32_t len) {
  addr = mem_addr_trans(addr);
  mem_ref->store(addr, len, data);
}

void diff_mem_read(uint32_t addr, uint8_t *data, uint32_t len) {
  mem_ref->load(addr, len, data);
}

void diff_step() {
  processor_t *c = model->get_core(0);
  c->step(1);
}

int diff_cmp() {

  int err = 0;

  processor_t *c = model->get_core(0);

  // PC
  uint32_t pc = c->get_state()->pc;
  if (emu_pc() != pc) {
    ERR("[DIFF] pc@DUT = 0x%08X, @REF = 0x%08X", emu_pc(), pc);
    err = 1;
  }

  // Reg
  for (int i = 0; i < 31; i++) {
    uint32_t xpr = (uint32_t)c->get_state()->XPR[i + 1];
    const char *abi = get_reg_abi(i + 1);
    if (emu_reg(i + 1) != xpr) {
      ERR("[DIFF] %s@DUT = 0x%08X, @REF = 0x%08X", abi, emu_reg(i + 1), xpr);
      err = 1;
    }
  }

  return err;
}

int diff_cmp_mem(uint32_t addr, uint32_t len) {
  int err = 0;

  uint32_t paddr = addr;

  addr = mem_addr_trans(addr);
  uint8_t *ptr = mem_ptr() + addr;

  uint8_t *ref = (uint8_t *)malloc(len);
  mem_ref->load(addr, len, ref);

  for (int i = 0; i < len; i++) {
    if (*(ptr + i) != *(ref + i)) {
      ERR("[DIFF] 0x%08X@DUT = 0x%08X, @REF = 0x%08X", paddr + i, *(ptr + i),
          *(ref + i));
      err = 1;
    }
  }

  free(ref);

  return err;
}