#ifndef __INTRINSIC_H
#define __INTRINSIC_H

#define CSRRW(rd, csr, rs)                                                     \
  asm volatile("csrrw %0," #csr ",%1;" : "=r"(rd) : "r"(rs))

#define CSRRS(rd, csr, rs)                                                     \
  asm volatile("csrrs %0," #csr ",%1;" : "=r"(rd) : "r"(rs))

#define CSRRC(rd, csr, rs)                                                     \
  asm volatile("csrrc %0," #csr ",%1;" : "=r"(rd) : "r"(rs))

#define CSRR(rd, csr) asm volatile("csrr %0," #csr : "=r"(rd))

#define CSRW(csr, rs) asm volatile("csrw " #csr ",%0" :: "r"(rs))

#define ECALL() asm volatile("ecall")

#define MRET() asm volatile("mret")

#endif // __INTRINSIC_H