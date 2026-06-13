#ifndef __COMMON_H
#define __COMMON_H

#include <stddef.h>
#include <stdint.h>

typedef uint8_t u8;
typedef int8_t s8;
typedef uint16_t u16;
typedef int16_t s16;
typedef uint32_t u32;
typedef int32_t s32;
typedef size_t usize;

void stop(int ret);

#define CSRRW(rd, csr, rs)                                                     \
  asm volatile("csrrw %0," #csr ",%1;" : "=r"(rd) : "r"(rs))

#define CSRRS(rd, csr, rs)                                                     \
  asm volatile("csrrs %0," #csr ",%1;" : "=r"(rd) : "r"(rs))

#define CSRRC(rd, csr, rs)                                                     \
  asm volatile("csrrc %0," #csr ",%1;" : "=r"(rd) : "r"(rs))

#endif // __COMMON_H