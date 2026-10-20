#ifndef __DIFFTEST_H
#define __DIFFTEST_H

#include <stdint.h>

bool diff_enabled();

void diff_init();
void diff_cleanup();

void diff_mem_write(uint32_t addr, uint8_t *data, uint32_t len);
void diff_mem_read(uint32_t addr, uint8_t *data, uint32_t len);

void diff_step();

int diff_cmp();

#endif // __DIFFTEST_H