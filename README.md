# HPipe
Hamster's Pipelined RISC-V CPU Core

This design is for learning purpose only


## Quick Start

Export to SystemVerilog
```shell
make verilog
```

Run specific App with HPipe simulator
```shell
make sim APP=rv-tests    # Folder names in `sim/app`, except `common`, are valid app names
make wave APP=rv-tests   # With waveform generated under `sim/app/build/<name>/<name>.fst`
```

Run with a GDB session
```shell
make gdb-server

# In another shell
make gdb
```


## Architecture

This Core implements a classical 5-stage pipelined CPU.  

Currently it only supports RV32I, but more extensions are on the way.

## Road Map
- [x] Enable GDB Debugging
- [x] Testbenches & Unit Tests using `verilator` and [`riscv-tests`](https://github.com/riscv-software-src/riscv-tests)  
- [ ] RV32M extension  
- [ ] Yosys-based backend analysis
- [ ] Branch Prediction & Optimized Branch Penalty
- [ ] CSRs supporting M mode
- [ ] L1 Cache & TLB
- [ ] AXI Bus

## ScreenShots
1. HPipe under simulation, passing every isa test in `riscv-tests`
![HPipe under simulation, passing every isa test in `riscv-tests`](docs/assets/sim.png)

1. Waveform of a running HPipe
![Waveform when HPipe is running](docs/assets/waveform.png)