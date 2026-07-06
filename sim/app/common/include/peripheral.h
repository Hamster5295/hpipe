#ifndef __PERIPHERAL_H
#define __PERIPHERAL_H

#include "common.h"

#define UART_BASE 0x10000000

void uart_send(u8 c);


#endif // __PERIPHERAL_H