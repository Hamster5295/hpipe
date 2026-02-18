#include "debug.h"
#include "emu.h"

extern "C" {
#include "gdbstub.h"
}

const char *HOST = "127.0.0.1:5555";

int main() {
  INFO("Starting HPipe Simulation");
  auto ops = emu_init();

  INFO("Emulator Initialized");
  INFO("GDB Server running at %s", HOST);

  gdbstub_t handle;
  if (!gdbstub_init(&handle, &ops,
                    (arch_info_t){
                        .target_desc = (char *)TARGET_RV32,
                        .smp = 1,
                        .reg_num = 33,
                    },
                    (char *)HOST)) {
    ERR("Cannot establish gdb server at %s", HOST);
    return -1;
  }

  INFO("GDB Session created, starting...");

  if (!gdbstub_run(&handle, NULL)) {
    ERR("Cannot run gdb server at %s", HOST);
    return -1;
  }

  gdbstub_close(&handle);
  emu_cleanup();
}