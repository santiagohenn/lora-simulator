# Archive

`de_history_3.csv` — per-generation log of an earlier differential-evolution run
(columns as written by `deoptimization.GenerationRecord`). It was produced with bounds and an objective
that differ from the current `deoptimization/DEMain.java` (e.g. heights outside the current 500–600 km
range, and positive best profits with 0 % access, which the current zero-access penalty would make
negative), so it cannot be regenerated with the current code. Kept for traceability only.
