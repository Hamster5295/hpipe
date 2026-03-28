#include "peripheral.h"

void uart_send(uint8_t c) { *(volatile uint8_t *)UART_BASE = c; }

uint32_t rtc_get() { return *(volatile uint32_t *)RTC_BASE; }