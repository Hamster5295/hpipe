#include "common.h"
#include "hprintf.h"
#include "intrinsic.h"

void handler() {
  hprintf("This is handler!\n");

  int cause = 0;
  CSRRW(cause, mcause, cause);
  hprintf("mcause = %d\n", cause);

  int ppc = 0;
  CSRR(ppc, mepc);
  CSRW(mepc, ppc + 4);

  MRET();
  stop(1);
}

int main() {
  CSRW(mtvec, handler);
  ECALL();
  hprintf("Back to main! \n");
  return 0;
}
