#include "peripheral.h"
#include "debug.h"
#include "emu.h"
#include <stdio.h>

bool is_peripheral(uint32_t addr) { return (addr >> 28) == 0x4; }

uint32_t peripheral_addr_trans(uint32_t addr) { return addr & 0xFFFF; }

bool is_valid_ascii(char c) {
  return (c >= ' ' && c <= '~') || (c == '\n' || c == '\r');
}

uint32_t peripheral_read(uint32_t addr) {
  uint32_t paddr = peripheral_addr_trans(addr);
  switch (paddr & 0xF000) {
  case RTC_BASE:
    DBG("RTC Read: %d", emu_get_cycles());
    return emu_get_cycles();
  }

  WARN("[Peripheral] Read with invalid address 0x%08X, falling back to 0",
       addr);
  return 0;
}

void peripheral_write(uint32_t addr, uint8_t data) {
  uint32_t paddr = peripheral_addr_trans(addr);
  switch (paddr & 0xF000) {
  case RTC_BASE:
    WARN("[Peripheral] Write to READONLY RTC at 0x%08X", addr);
    return;

  case UART_BASE:
    DBG("UART Send: %c", data);
    if (!is_valid_ascii(data)) {
      ERR("[UART] Invalid character '%2X' sent!", data);
    }
    printf("%c", data);
    return;
  }
}
