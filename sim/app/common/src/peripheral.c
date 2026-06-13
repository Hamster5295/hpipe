#include "peripheral.h"

void uart_send(uint8_t c) { *(volatile u8 *)UART_BASE = c; }

uint32_t rtc_get() { return *(volatile u32 *)RTC_BASE; }