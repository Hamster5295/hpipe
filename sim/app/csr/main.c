#include "common.h"
#include "hprintf.h"

int main() {
  int lastI = 0, ret = 0;
  for (int i = 1; i < 1024; i++) {
    CSRRW(ret, mtval, i);
    if (ret != lastI) {
      hprintf("CSR RW Failed: mepc expected 0x%X, get 0x%X\n", lastI, ret);
      stop(1);
    }
    lastI = i;
  }
  for (int i = 1; i < 1024; i += 2) {
    CSRRW(ret, mtval, i);
    CSRRW(ret, mtval, i + 1);
    if (ret != i) {
      hprintf("CSR continuous RW Failed: mepc expected 0x%X, get 0x%X\n", i + 1,
              ret);
      stop(1);
    }
  }
}