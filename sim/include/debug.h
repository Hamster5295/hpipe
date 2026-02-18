#ifndef __DEBUG_H
#define __DEBUG_H

#include "config.h"

#include <stdio.h>
#include <stdlib.h>

#define ANSI_FG_BLACK "\33[1;30m"
#define ANSI_FG_RED "\33[1;31m"
#define ANSI_FG_GREEN "\33[1;32m"
#define ANSI_FG_YELLOW "\33[1;33m"
#define ANSI_FG_BLUE "\33[1;34m"
#define ANSI_FG_MAGENTA "\33[1;35m"
#define ANSI_FG_CYAN "\33[1;36m"
#define ANSI_FG_WHITE "\33[1;37m"
#define ANSI_BG_BLACK "\33[1;40m"
#define ANSI_BG_RED "\33[1;41m"
#define ANSI_BG_GREEN "\33[1;42m"
#define ANSI_BG_YELLOW "\33[1;43m"
#define ANSI_BG_BLUE "\33[1;44m"
#define ANSI_BG_MAGENTA "\33[1;45m"
#define ANSI_BG_CYAN "\33[1;46m"
#define ANSI_BG_WHITE "\33[1;47m"
#define ANSI_NONE "\33[0m"
#define ANSI_FG_GREEN_NORM "\33[0;32m"

#ifndef DISABLE_COLOR

#define ANSI_FG_MTRACE "\033[38;5;208m"
#define ANSI_FG_MTRACE_LIGHT "\033[38;5;220m"
#define ANSI_FG_ITRACE "\033[38;5;56m"
#define ANSI_FG_ITRACE_LIGHT "\033[38;5;183m"
#define ANSI_FG_FTRACE "\033[38;5;40m"
#define ANSI_FG_FTRACE_LIGHT "\033[38;5;193m"
#define ANSI_FG_DTRACE "\033[38;5;45m"
#define ANSI_FG_DTRACE_LIGHT "\033[38;5;159m"
#define ANSI_FG_RD_LIGHT "\033[38;5;217m"

#else   // DISABLE_COLOR

#define ANSI_FG_MTRACE 
#define ANSI_FG_MTRACE_LIGHT 
#define ANSI_FG_ITRACE
#define ANSI_FG_ITRACE_LIGHT 
#define ANSI_FG_FTRACE 
#define ANSI_FG_FTRACE_LIGHT 
#define ANSI_FG_DTRACE 
#define ANSI_FG_DTRACE_LIGHT 
#define ANSI_FG_RD_LIGHT 

#endif  // DISABLE_COLOR


#ifdef ENABLE_LOG

#define INFO(msg, ...)                                                         \
  printf(ANSI_FG_CYAN "[I] " ANSI_NONE msg "\n", ##__VA_ARGS__)

#define WARN(msg, ...)                                                         \
  printf(ANSI_FG_YELLOW "[W] " ANSI_NONE msg "\n", ##__VA_ARGS__)

#define ERR(msg, ...)                                                          \
  printf(ANSI_FG_RED "[E] " ANSI_FG_RD_LIGHT msg ANSI_NONE "\n", ##__VA_ARGS__)

#ifdef ENABLE_LOG_DBG

#define DBG(msg, ...)                                                          \
  printf(ANSI_FG_BLACK "[D] " msg ANSI_NONE "\n", ##__VA_ARGS__)
#else
#define DBG(msg, ...)
#endif

#else

#define INFO(msg, ...)
#define WARN(msg, ...)
#define ERR(msg, ...)
#define DBG(msg, ...)

#endif


#ifdef ENABLE_ASSERT

#define CHECK(expr, msg, ...)                                                  \
  do {                                                                         \
    if (!(expr)) {                                                             \
      ERR(msg, ##__VA_ARGS__);                                                 \
      exit(1);                                                                 \
    }                                                                          \
  } while (0)

#define CHECK_NPC(expr, msg, ...)                                              \
  do {                                                                         \
    if (!(expr)) {                                                             \
      ERR(msg, ##__VA_ARGS__);                                                 \
      npc_state = NPC_ABORT;                                                   \
    }                                                                          \
  } while (0)

#define CHECK_NULL(x) CHECK(x, #x " is NULL")

#else
#define CHECK(expr, msg, ...)
#define CHECK_NULL(x)
#endif

#define PRINT(msg, ...) printf(msg "\n", ##__VA_ARGS__)

#define PANIC(msg, ...)                                                        \
  do {                                                                         \
    ERR(msg, ##__VA_ARGS__);                                                   \
    exit(1);                                                                   \
  } while (0)

#endif // __DEBUG_H