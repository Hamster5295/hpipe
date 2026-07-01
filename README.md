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
make gdb APP=rv-tests
```

Run Backend Analysis:

Use vivado to run a FPGA synthesis and get reports
```bash
make init-fpga # This only need to run once
make fpga
```

Use yosys-based tools to run a ASIC synthesis and get reports
```bash
make init-asic # This only need to run once
               # Might require a really long time as PDK will be downloaded
make asic
```

## Architecture

This Core implements a 6-stage pipelined CPU.  

Currently it supports RV32IM_izcsr, but more extensions are on the way.


### IF & BTB

The IF stage involves a 16-entry BTB with 2-bit saturation counters for each entry, adopting **PLRU** substitution strategy.

The Penalty of branch miss is **2 cycles**.


### ID

The ID stage decodes each instruction into uops that determines the behavour of EX and MEM stages.


### SG

The SG stage differs HPipe from classical 5-stage CPUs.  

SG is short of "Source Generation", at which stage would the oprands for EX and MEM stages be generated through either reading `RegFile`, `CsrFile` or receiving from Feed-Forwards.

This stage is introduced as timing is not sufficient for `Decoder` and Source Generation to work in the same stage.


### EX

The EX stage currently uses **Carry Lookahead Adder**, **Radix-4 Booth Multiplier** and **Non-restoring remainder Divider** as its arithmatic implemention

- The **Carry Lookahead Adder** implements a *group-of-4* hierarchy, i.e. at most 4 adders shares a **Carry Lookahead Unit**
- The **Radix-4 Booth Multiplier** currently produces result in 1 cycle, which might have impact on frequency
- The **Non-restoring remainder Divider** requires 32 cycles for a normal divide operation

Note that DSP Macros are used when specified `Fpga = true` in parameters


### MEM

The MEM stage uses a simple interface to interact with MMIOs. AXI support is planned.


### WB

The WB stage signals whether a valid instruction is retired.


### Feed Forward

There're 2 primary paths to forward:

1. SG

The SG stage requires up-to-date source data (`rs1` & `rs2`). Thus, if `rs1` or `rs2` will be written by the instructions executing in EX or MEM stage, the dest data should be forwarded to SG.

- EX -> SG: No stalls/bubbles required
- MEM -> SG: No stalls/bubbles required
- EX(needs MEM) -> SG: 1 cycle's stall


2. IF

The IF stage requires `rs1` to generate the address of `JALR` to predict jump location.  

If `rs1` can be forwarded, BTB will predict correctly, as `JALR` always takes branch; otherwise, BTB won't predict, as dest pc is not known for now.

- ID -> IF: Will not predict
- SG -> IF: Will not predict
- EX -> IF: Will not predict (Even though it can, this is for performance consideration)
- MEM -> IF: Predict


## PPA

### FPGA

Characteristics were estimated using Xilinx part `xc7a200t`

- Power = 431mW
- Clock Freq = 151.5MHz
- Utilization
  - LUT = 3533
  - FF  = 3205
  - DSP = 4


### ASIC

Characteristics were estimated using [icsprout55](https://github.com/openecos-projects/icsprout55-pdk) 55nm PDK

- Power = 744.4mW
- Clock Freq = 685.184MHz
- Area = 30881.2nm2

The analysis above was taken under `v0.1.3`. 

However, after M extension was implemented, the `yosys` takes too long to synthesis, so no valid backend report was created then.


## Road Map
- [x] Enable GDB Debugging
- [x] Testbenches & Unit Tests using `verilator` and [`riscv-tests`](https://github.com/riscv-software-src/riscv-tests)  
- [x] RV32M extension  
- [x] Yosys-based backend analysis
- [x] Branch Prediction & Optimized Branch Penalty
- [x] CSRs supporting M mode
- [ ] L1 Cache & TLB
- [ ] AXI Bus
- [x] Coremark Bench

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
