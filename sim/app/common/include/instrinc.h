#ifndef __INSTRINC_H
#define __INSTRINC_H

#define CSRRW(rd, csr, rs)                                                     \
  asm volatile("csrrw %0," #csr ",%1;" : "=r"(rd) : "r"(rs))

#define CSRRS(rd, csr, rs)                                                     \
  asm volatile("csrrs %0," #csr ",%1;" : "=r"(rd) : "r"(rs))

#define CSRRC(rd, csr, rs)                                                     \
  asm volatile("csrrc %0," #csr ",%1;" : "=r"(rd) : "r"(rs))

#define CSRR(rd, csr) asm volatile("csrr %0," #csr : "=r"(rd))

#define ECALL() asm volatile("ecall")

#define MRET() asm volatile("mret")

#endif // __INSTRINC_H