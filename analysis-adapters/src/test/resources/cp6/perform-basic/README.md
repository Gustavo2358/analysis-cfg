# CP6 PERFORM BASIC real sources

Three positive COBOL sources: literal paragraph, literal plus data copy, and strong
overwrite of OLDPROG before PERFORM. All expect CALLER -> PROGA, raw `PROGA   `,
modelValueRemainder=false and support at the original literal MOVE. The paragraph
is not an external dependency. Source/interpretation remainders remain explicit.

`python3 -B scripts/project/e2e_perform_basic.py --work <new-directory> --producers <producers.json>`
runs exact locked producers twice and compares SP/AIR/CFG/dependency bytes. It also
permutes AIR sequences. `lean.py qualification-local` builds the producers and
executes this real E2E. Remote FAST runs only the independent AIR model fixture.
