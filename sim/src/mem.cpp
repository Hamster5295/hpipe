#include "mem.h"
#include "debug.h"
#include "emu.h"
#include "peripheral.h"

uint8_t mem[MEM_SIZE];

/// Foundamental API

uint8_t *mem_ptr() { return mem; }

uint32_t mem_addr_trans(uint32_t addr) { return addr - RESET_VECTOR; }

uint32_t mem_read(uint32_t addr, bool *exception) {

  if (is_peripheral(addr)) {
    return peripheral_read(addr, exception);
  }

  uint32_t paddr = mem_addr_trans(addr);
  if (paddr >= MEM_SIZE) {
    ERR("Invalid memory read at 0x%08X: Out of range 0x%08X", addr,
        RESET_VECTOR + MEM_SIZE);
    *exception = true;
    return 0;
  }

  uint32_t result = mem[paddr + 3] << 24 | mem[paddr + 2] << 16 |
                    mem[paddr + 1] << 8 | mem[paddr];

  DBG("[Mem] R [0x%08X] -> 0x%08X", addr, result);

  return result;
}

void mem_write(uint32_t addr, uint32_t data, uint32_t mask, bool *exception) {

  if (is_peripheral(addr)) {
    for (int i = 0; i < 4; i++) {
      if ((mask >> i) & 0x1)
        peripheral_write(addr + i, 0xFF & (data >> i * 8), exception);
    }
    return;
  }

  uint32_t paddr = mem_addr_trans(addr);

  if (paddr >= MEM_SIZE) {
    ERR("Invalid memory write at 0x%08X: Out of range 0x%08X", addr,
        RESET_VECTOR + MEM_SIZE);
    *exception = true;
    return;
  }

  DBG("[Mem] W [0x%08X] <- 0x%08X", addr, data);

  for (int i = 0; i < 4; i++) {
    if ((mask >> i) & 0x1)
      mem[paddr + i] = 0xFF & (data >> i * 8);
  }
}

/// Memory Queue & Delays

#define MEM_INST_DELAY 1
#define MEM_DATA_DELAY 1

mem_resp_t inst_queue[MEM_INST_DELAY];
mem_resp_t data_queue[MEM_DATA_DELAY];

void mem_queue_step(mem_resp_t *queue, int delay) {
  for (int i = delay - 2; i >= 0; i--) {
    queue[i + 1] = queue[i];
  }
  queue[0].valid = false;
}

bool mem_queue_req(mem_resp_t *queue, uint32_t addr) {
  if (queue[0].valid)
    return false; // full

  bool excp;
  queue[0] = {.valid = true, .data = mem_read(addr, &excp), .excp = excp};
  return true;
}

bool mem_queue_resp(mem_resp_t *queue, int delay, mem_resp_t *result) {
  if (!queue[delay - 1].valid)
    return false; // no output at queue head

  *result = queue[delay - 1];
  return true;
}

void mem_inst_step() { mem_queue_step(inst_queue, MEM_INST_DELAY); }
bool mem_inst_req(uint32_t addr) { return mem_queue_req(inst_queue, addr); }
bool mem_inst_resp(mem_resp_t *result) { return mem_queue_resp(inst_queue, MEM_INST_DELAY, result); }

void mem_data_step() { mem_queue_step(data_queue, MEM_DATA_DELAY); }
bool mem_data_req(uint32_t addr) { return mem_queue_req(data_queue, addr); }
bool mem_data_resp(mem_resp_t *result) { return mem_queue_resp(data_queue, MEM_DATA_DELAY, result); }
