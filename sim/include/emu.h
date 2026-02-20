#ifndef __EMU_H
#define __EMU_H

#define MEM_SIZE 16384
#define MAX_CYCLE_PER_INST 32

#define RESET_VECTOR 0x80000000

#define REGS                                                                   \
  REG(0);                                                                      \
  REG(1);                                                                      \
  REG(2);                                                                      \
  REG(3);                                                                      \
  REG(4);                                                                      \
  REG(5);                                                                      \
  REG(6);                                                                      \
  REG(7);                                                                      \
  REG(8);                                                                      \
  REG(9);                                                                      \
  REG(10);                                                                     \
  REG(11);                                                                     \
  REG(12);                                                                     \
  REG(13);                                                                     \
  REG(14);                                                                     \
  REG(15);                                                                     \
  REG(16);                                                                     \
  REG(17);                                                                     \
  REG(18);                                                                     \
  REG(19);                                                                     \
  REG(20);                                                                     \
  REG(21);                                                                     \
  REG(22);                                                                     \
  REG(23);                                                                     \
  REG(24);                                                                     \
  REG(25);                                                                     \
  REG(26);                                                                     \
  REG(27);                                                                     \
  REG(28);                                                                     \
  REG(29);                                                                     \
  REG(30);

struct target_ops emu_init(char *fst);
void emu_run(char *file);
int emu_cleanup();

#endif