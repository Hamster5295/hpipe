#ifndef __PERIPHERAL_H
#define __PERIPHERAL_H

#include "common.h"

#define PERI_BASE 0x40000000
#define PERI_ADDR(addr) (PERI_BASE + addr)

#define UART_BASE PERI_ADDR(0x1000)
#define RTC_BASE PERI_ADDR(0x2000)

void uart_send(u8 c);
uint32_t rtc_get();


#endif // __PERIPHERAL_H