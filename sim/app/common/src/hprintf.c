#include "hprintf.h"
#include "peripheral.h"
#include <stdarg.h>

static void hputchar(char c) { uart_send(c); }

static usize hstrlen(const char *s) {
  usize len = 0;
  while (s[len])
    ++len;
  return len;
}

// 输出字符串（不加宽度处理）
static void print_raw_string(const char *s) {
  while (*s)
    hputchar(*s++);
}

// 输出字符串（支持宽度和左/右对齐）
static void hprint_string(const char *s, int width, int left_align) {
  if (!s)
    s = "(null)";
  int len = (int)hstrlen(s);
  int pad = (width > len) ? (width - len) : 0;

  if (!left_align) {
    for (int i = 0; i < pad; ++i)
      hputchar(' ');
  }
  print_raw_string(s);
  if (left_align) {
    for (int i = 0; i < pad; ++i)
      hputchar(' ');
  }
}

// 输出单个字符（支持宽度）
static void hprint_char_width(char c, int width, int left_align) {
  if (!left_align) {
    for (int i = 1; i < width; ++i)
      hputchar(' ');
  }
  hputchar(c);
  if (left_align) {
    for (int i = 1; i < width; ++i)
      hputchar(' ');
  }
}

// 将无符号整数按指定进制转换为字符串（存入buffer，返回长度）
static int hutoa_buf(unsigned int value, char *buf, int base, int upper) {
  const char *digits_lower = "0123456789abcdef";
  const char *digits_upper = "0123456789ABCDEF";
  const char *digits = upper ? digits_upper : digits_lower;
  char *p = buf;
  do {
    *p++ = digits[value % base];
    value /= base;
  } while (value);
  int len = (int)(p - buf);
  // 反转字符串
  for (int i = 0; i < len / 2; ++i) {
    char tmp = buf[i];
    buf[i] = buf[len - 1 - i];
    buf[len - 1 - i] = tmp;
  }
  return len;
}

// 核心数字输出（处理符号、宽度、对齐、填充）
static void hprint_number(int sign, unsigned int uval, int base, int upper,
                          int width, int pad_zero, int left_align) {
  char buf[32];
  int len = hutoa_buf(uval, buf, base, upper);
  int total_len = len + (sign ? 1 : 0);
  int pad = (width > total_len) ? (width - total_len) : 0;

  if (!left_align && !pad_zero) { // 右对齐，空格填充
    for (int i = 0; i < pad; ++i)
      hputchar(' ');
    if (sign)
      hputchar('-');
    print_raw_string(buf);
  } else if (!left_align && pad_zero) { // 右对齐，零填充
    if (sign)
      hputchar('-');
    for (int i = 0; i < pad; ++i)
      hputchar('0');
    print_raw_string(buf);
  } else { // 左对齐
    if (sign)
      hputchar('-');
    print_raw_string(buf);
    for (int i = 0; i < pad; ++i)
      hputchar(' ');
  }
}

// 有符号十进制整数
static void hprint_int(int val, int width, int pad_zero, int left_align) {
  if (val < 0) {
    hprint_number(1, (unsigned int)(-val), 10, 0, width, pad_zero, left_align);
  } else {
    hprint_number(0, (unsigned int)val, 10, 0, width, pad_zero, left_align);
  }
}

// 无符号十进制整数
static void hprint_unsigned(unsigned int val, int width, int pad_zero,
                            int left_align) {
  hprint_number(0, val, 10, 0, width, pad_zero, left_align);
}

// 十六进制整数（大小写可选）
static void hprint_hex(unsigned int val, int upper, int width, int pad_zero,
                       int left_align) {
  hprint_number(0, val, 16, upper, width, pad_zero, left_align);
}

// 内部变参格式化输出
static int hvprintf(const char *fmt, va_list ap) {
  while (*fmt) {
    if (*fmt != '%') {
      hputchar(*fmt++);
      continue;
    }
    fmt++; // 跳过 '%'

    // 解析标志
    int left_align = 0;
    int pad_zero = 0;
    while (*fmt == '-' || *fmt == '0') {
      if (*fmt == '-')
        left_align = 1;
      else if (*fmt == '0')
        pad_zero = 1;
      fmt++;
    }

    // 解析宽度（仅支持数字，不支持 '*'）
    int width = 0;
    while (*fmt >= '0' && *fmt <= '9') {
      width = width * 10 + (*fmt - '0');
      fmt++;
    }

    // 读取格式说明符
    char spec = *fmt++;
    switch (spec) {
    case 'd':
    case 'i':
      hprint_int(va_arg(ap, int), width, pad_zero, left_align);
      break;
    case 'u':
      hprint_unsigned(va_arg(ap, unsigned int), width, pad_zero, left_align);
      break;
    case 'x':
      hprint_hex(va_arg(ap, unsigned int), 0, width, pad_zero, left_align);
      break;
    case 'X':
      hprint_hex(va_arg(ap, unsigned int), 1, width, pad_zero, left_align);
      break;
    case 's': {
      char *s = va_arg(ap, char *);
      hprint_string(s, width, left_align);
      break;
    }
    case 'c': {
      char c = (char)va_arg(ap, int);
      if (width > 1)
        hprint_char_width(c, width, left_align);
      else
        hputchar(c);
      break;
    }
    case '%':
      hputchar('%');
      break;
    default:
      hputchar('%');
      hputchar(spec);
      break;
    }
  }
  return 0;
}

void hprintf(char *fmt, ...) {
  va_list ap;
  va_start(ap, fmt);
  hvprintf(fmt, ap);
  va_end(ap);
}