#include "debug.h"
#include "peripheral.h"
#include <stdio.h>

uint32_t uart_read(uint32_t addr) {}

void uart_write(uint32_t addr, uint8_t data) {
  DBG("UART Send: %c", data);
  putchar(data);
}

__attribute__((constructor)) void uart_init() {
  peripheral_t p = {
      .addr_h12 = 0x100, .read = uart_read, .write = uart_write, .step = NULL};
  peripheral_add(p);
}