#ifndef __PERIPHERAL_H
#define __PERIPHERAL_H

#include <stdint.h>

#define UART_BASE 0x1000
#define RTC_BASE 0x2000

bool is_peripheral(uint32_t addr);
uint32_t peripheral_read(uint32_t addr);
void peripheral_write(uint32_t addr, uint8_t data);

#endif // __PERIPHERAL_H