#ifndef __PERIPHERAL_H
#define __PERIPHERAL_H

#include "VHPipe.h"
#include <stdint.h>

#define UART_BASE 0x10000000
#define RTC_BASE 0x20000000

typedef struct peripheral_t {
  uint32_t addr_h12;
  uint32_t (*read)(uint32_t addr);
  void (*write)(uint32_t addr, uint8_t data);
  void (*step)(VHPipe *);
} peripheral_t;

bool is_peripheral(uint32_t addr);
uint32_t peripheral_read(uint32_t addr, bool* exception);
void peripheral_write(uint32_t addr, uint8_t data, bool* exception);
void peripheral_step(VHPipe* cpu);

void peripheral_add(peripheral_t peri);

#endif // __PERIPHERAL_H