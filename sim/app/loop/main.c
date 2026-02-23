#include "common.h"

int main() {
  volatile int a = 0;
  for (int i = 0; i < 20; i++) {
    a += 1;
  }

  if (a != 20)
    stop(1);
  return 0;
}