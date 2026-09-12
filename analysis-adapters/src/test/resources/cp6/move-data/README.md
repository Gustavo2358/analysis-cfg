# Real MOVE data chains

The four COBOL inputs are independent source oracles for one-hop, multi-hop,
strong overwrite and snapshot. Every CALLER resolves PROGA with raw `PROGA   `
and modelValueRemainder=false at BEFORE Invoke. Other source/name remainders
remain explicit. No AIR fixture substitutes these programs.

Run locally via `python3 -B scripts/harness/lean.py qualification-local`.
It builds the producers in isolated checkouts at the exact source-lock commits,
then runs `scripts/project/e2e_move_data.py` through SP, AIR, CFG, PossibleValues
and dependency publication. Intermediate products and logs remain in the build
output; they are not receipts. Remote FAST uses small model tests only.
