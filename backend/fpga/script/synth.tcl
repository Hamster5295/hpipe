open_project hpipe.xpr
synth_design -top HPipe
report_timing -nworst 20 > ../reports/timing.rpt
report_utilization > ../reports/util.rpt
report_power > ../reports/power.rpt