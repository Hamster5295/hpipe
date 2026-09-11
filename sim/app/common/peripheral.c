#include "peripheral.h"

void uart_send(uint8_t c) { *(volatile u8 *)UART_BASE = c; }