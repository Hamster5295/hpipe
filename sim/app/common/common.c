#include "common.h"

void stop(int ret) {
  asm("ebreak");
  while (1) {
  }
}

