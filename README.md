# HPipe
Hamster's Pipelined RISC-V CPU Core

This design is for learning purpose


## Quick Start

Export to SystemVerilog
```bash
make verilog
```

Run specific App with HPipe simulator
```bash
make sim APP=rv-tests    # Folder names in `sim/app`, except `common`, are valid app names
make wave APP=rv-tests   # With waveform generated under `sim/app/build/<name>/<name>.fst`
```

Run with a GDB session
```bash
make gdb-server

# In another shell
make gdb
```

Run Backend Analysis
```bash
make init-backend # This only need to run once
make backend
```

## Architecture

This Core implements a classical 5-stage pipelined CPU.  

Currently it only supports RV32I, but more extensions are on the way.


## PPA

All characteristics were estimated under [icsprout55](https://github.com/openecos-projects/icsprout55-pdk) 55nm PDK

- Power = 239.3mW
- Clock Freq = 665Hz
- Area = 21621.6nm2


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


## Acknowledgements

This project exists with the help of the Open Source Community.  

Thanks
- [The YSYX Project](https://ysyx.oscc.cc/) for providing a comprehensive walkthough of the Processor full-stack design & validation, along with a set of open source projects. The transplant of `riscv-tests` and `backend` won't exist without it.
- [mini-gdbstub](https://github.com/RinHizakura/mini-gdbstub) for providing a really simple gdb server framework
