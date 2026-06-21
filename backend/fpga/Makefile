VIVADO ?= vivado

init:
	@mkdir -p vivado
	@mkdir -p reports
	@cd vivado && $(VIVADO) -mode batch -source ../script/init.tcl

run:
	@cd vivado && $(VIVADO) -mode batch -source ../script/synth.tcl