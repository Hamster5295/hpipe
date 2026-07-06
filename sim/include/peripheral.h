#ifndef __PERIPHERAL_H
#define __PERIPHERAL_H

#include "VHPipe.h"
#include <stdint.h>

#define UART_BASE 0x1000
#define RTC_BASE 0x2000

#define PLIC_BASE       0x0C000000
#define PLIC_NDEV       32

void plic_set_source(int src, bool asserted);

typedef struct peripheral_t {
  uint32_t addr_h12;
  uint32_t (*read)(uint32_t addr);
  void (*write)(uint32_t addr, uint8_t data);
  void (*step)(VHPipe *);
} peripheral_t;

bool is_peripheral(uint32_t addr);
uint32_t peripheral_read(uint32_t addr);
void peripheral_write(uint32_t addr, uint8_t data);
void peripheral_step(VHPipe* cpu);

void peripheral_add(peripheral_t peri);

#endif // __PERIPHERAL_H