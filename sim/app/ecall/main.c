#include "common.h"
#include "hprintf.h"
#include "instrinc.h"

void handler() {
  hprintf("This is handler!\n");

  int cause = 0;
  CSRRW(cause, mcause, cause);
  hprintf("mcause = %d\n", cause);

  int ppc = 0;
  CSRRW(ppc, mepc, ppc);
  CSRRW(ppc, mepc, ppc + 4);

  MRET();
  stop(1);
}

int main() {
  int i = 0;
  CSRRW(i, mtvec, handler);
  ECALL();
  hprintf("Back to main!");
  return 0;
}
