#include "peripheral.h"
#include "debug.h"
#include <cstddef>
#include <stdio.h>

peripheral_t peris[32] = {};
uint32_t peri_count = 0;

bool is_peripheral(uint32_t addr) { return (addr >> 28) != 0x8; }
uint32_t peripheral_addr_trans(uint32_t addr) { return addr & 0xFFFF; }
void peripheral_add(peripheral_t peri) { peris[peri_count++] = peri; }

uint32_t peripheral_read(uint32_t addr) {

  for (int i = 0; i < peri_count; i++) {
    peripheral_t peri = peris[i];
    if ((addr >> 20) != peri.addr_h12)
      continue;
    if (peri.read == NULL) {
      WARN("[Peripheral] Read with non-readable peripheral address 0x%08X, "
           "falling back to 0",
           addr);
      return 0;
    }
    return peri.read(addr);
  }

  WARN("[Peripheral] Read with invalid address 0x%08X, falling back to 0",
       addr);
  return 0;
}

void peripheral_write(uint32_t addr, uint8_t data) {

  for (int i = 0; i < peri_count; i++) {
    peripheral_t peri = peris[i];
    if ((addr >> 20) != peri.addr_h12)
      continue;
    if (peri.write == NULL) {
      WARN("[Peripheral] Read with non-writeable peripheral address 0x%08X, "
           "falling back to 0",
           addr);
      return;
    }
    peri.write(addr, data);
    return;
  }

  WARN("[Peripheral] Write with invalid address 0x%08X, falling back to 0",
       addr);
}

void peripheral_step(VHPipe *cpu) {
  for (int i = 0; i < peri_count; i++)
    if (peris[i].step != NULL)
      peris[i].step(cpu);
}