# HPipe
Hamster's Pipelined RISC-V CPU Core

This design is for learning purpose only


## Quick Start

Export to SystemVerilog
```shell
make verilog
```

Run RISC-V Tests with a GDB session
```shell
make sim

# In another shell
make gdb
```


## Architecture

This Core implements a classical 5-stage pipelined CPU.  

Currently it only supports RV32I, but more extensions are on the way.

## Road Map
- [x] Enable GDB Debugging
- [ ] Testbenches & Unit Tests using `verilator` and [`riscv-tests`](https://github.com/riscv-software-src/riscv-tests)  
- [ ] RV32M extension  
- [ ] Yosys-based backend analysis
- [ ] Branch Prediction & Optimized Branch Penalty
- [ ] CSRs supporting M mode
- [ ] L1 Cache & TLB
- [ ] AXI Bus