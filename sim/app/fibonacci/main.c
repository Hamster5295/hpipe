int fibonacci(int num) {
  if (num < 2)
    return 1;
  return fibonacci(num - 1) + fibonacci(num - 2);
}

int main() {
  volatile int a = 0;
  a = fibonacci(16);
  return a == 987;
}