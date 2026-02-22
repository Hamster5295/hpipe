#include "debug.h"
#include "emu.h"
#include <signal.h>

extern "C" {
#include "gdbstub.h"
}

const char *HOST = "127.0.0.1:5555";

void sig_handler(int sig) {
  if (sig == SIGINT) {
    INFO("SIGINT recved, Emu Cleaning Up");
    int ret = emu_cleanup();
    INFO(ANSI_FG_BLUE "===================" ANSI_NONE);
    exit(1);
  }
}

int main(int argc, char *argv[]) {

  signal(SIGINT, sig_handler);

  INFO(ANSI_FG_BLUE "====== HPipe ======" ANSI_NONE);

  char *batch_target = NULL;
  char *fst_path = NULL;
  if (argc > 0) {
    for (int i = 0; i < argc; i++) {
      char *target = argv[i];
      if (target[0] == '-' && target[1] == 'b')
        batch_target = target + 2;
      else if (target[0] == '-' && target[1] == 't')
        fst_path = target + 2;
    }
  }

  auto ops = emu_init(fst_path);

  INFO("Emulator Initialized");

  if (batch_target == NULL) {
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

    INFO("GDB Connected");

    if (!gdbstub_run(&handle, NULL)) {
      ERR("Cannot run gdb server at %s", HOST);
      return -1;
    }

    gdbstub_close(&handle);
    INFO("GDB disconnected, Cleaning up");
  } else {
    INFO("Run Emulator in batch mode with image '%s'", batch_target);
    emu_run(batch_target);
  }

  INFO("Emu Cleaning Up");
  int ret = emu_cleanup();
  INFO(ANSI_FG_BLUE "===================" ANSI_NONE);

  return ret;
}