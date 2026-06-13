#include "common.h"
#include "hprintf.h"

int main() {
  int lastI = 0, ret = 0;
  for (int i = 1; i < 1024; i++) {
    CSRRW(ret, mepc, i);
    if (ret != lastI) {
      hprintf("CSR RW Failed: mepc expected 0x%X, get 0x%X", lastI, ret);
      stop(1);
    }
    lastI = i;
  }
}