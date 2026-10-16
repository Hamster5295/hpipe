#ifndef __MEM_H
#define __MEM_H

#include <stdint.h>

typedef struct {
  bool valid;
  uint32_t data;
  bool excp;
} mem_resp_t;

uint8_t *mem_ptr();
uint32_t mem_read(uint32_t addr, bool *exception);
void mem_write(uint32_t addr, uint32_t data, uint32_t mask, bool *exception);
uint32_t mem_addr_trans(uint32_t addr);

void mem_inst_step();
bool mem_inst_req(uint32_t addr);
bool mem_inst_resp(mem_resp_t *result);

void mem_data_step();
bool mem_data_req(uint32_t addr);
bool mem_data_resp(mem_resp_t *result);

#endif // __MEM_H