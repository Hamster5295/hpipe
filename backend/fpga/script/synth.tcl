open_project hpipe.xpr

synth_design -top HPipe -directive PerformanceOptimized -retiming -flatten_hierarchy full

report_timing -sort_by group -max_paths 256 -path_type summary -file ../report/timing.summary.rpt
report_timing -sort_by group -nworst 20 -file ../report/timing.rpt
report_utilization -file ../report/util.rpt
report_power -file ../report/power.rpt