# HPipe
Hamster's Pipelined RISC-V CPU Core

This design is for learning purpose only


## Quick Start

Export to SystemVerilog
```shell
make verilog
```


## Architecture

This Core implements a classical 5-stage pipelined CPU.  

Currently it only supports RV32I, but more instruction sets are on the way.

## Road Map
- [ ] Testbenches & Unit Tests using `verilator` and [`riscv-tests`](https://github.com/riscv-software-src/riscv-tests)  
- [ ] RV32M extension  
- [ ] Branch Prediction & Optiized Branch Penalty
- [ ] CSRs supporting M mode
- [ ] L1 Cache & TLB
- [ ] AXI Bus