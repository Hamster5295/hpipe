#![no_std]
#![no_main]

use core::arch::global_asm;
global_asm!(include_str!("../../common/start.S"));

use core::panic::PanicInfo;

#[panic_handler]
fn panic(_info: &PanicInfo) -> ! {
    loop {}
}

const UART_BASE: usize = 0x1000_0000;

unsafe fn uart_putc(c: char) {
    unsafe { core::ptr::write_volatile(UART_BASE as *mut char, c) };
}

unsafe fn uart_puts(s: &str) {
    unsafe {
        for c in s.chars() {
            uart_putc(c);
        }
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn main() -> u32 {
    unsafe {
        uart_puts("Hello from rust!\n");
    }
    0
}
