#!/usr/bin/env python3
"""W5 source recipes and handwritten interval goldens, independent of all producers."""
DATA=['01 RAW-AREA.','   05 PREFIX-PART PIC X(4).','   05 SUFFIX-PART PIC X(4).','01 RAW-TEXT REDEFINES RAW-AREA PIC X(8).','01 SOURCE-TEXT PIC X(8).','01 SNAPSHOT PIC X(8).','01 SAVED-TEXT PIC X(8).','01 FLAG PIC X.']
def fragment(start,end,producer,source='RAW-TEXT',offset=None,captures=()):
 return dict(start=start,end=end,producer=producer,source=source,offset=start if offset is None else offset,captures=list(captures))
def expectation(obj,candidates,rd,model=False):return dict(object=obj,candidates=candidates,rd=rd,model=model)
def fixtures():
 cases={}
 def add(name,body,observations,*,fallback=False,control=()):
  lines=['IDENTIFICATION DIVISION.','PROGRAM-ID. W5-'+name.upper()+'.','DATA DIVISION.','WORKING-STORAGE SECTION.',*DATA,'PROCEDURE DIVISION.'];tags={}
  for row in body:
   tag,line=row if isinstance(row,tuple) else (None,row)
   if tag:tags[tag]=len(lines)+1
   lines.append(line)
  assert all(len(line)<=65 for line in lines)
  cases[name]=dict(source=''.join('       '+line+'\n' for line in lines),books={},tags=tags,observations=observations,fallback=fallback,control=list(control))
 old=('old',"MOVE 'ABCDEFGH' TO RAW-AREA.");prefix=('prefix',"MOVE 'WXYZ' TO PREFIX-PART.");call=('call','CALL RAW-TEXT.');end='GOBACK.'
 partial=[fragment(0,4,'prefix'),fragment(4,8,'old')]
 add('partial',[old,prefix,call,end],[expectation('RAW-TEXT',{'WXYZEFGH':partial},[('prefix',0,4),('old',4,8)])])
 captured=[dict(f,captures=['copy']) for f in partial]
 add('capture',[old,prefix,('copy','MOVE RAW-AREA TO SNAPSHOT.'),('late',"MOVE 'XXXXXXXX' TO RAW-AREA."),('call','CALL SNAPSHOT.'),end],[expectation('SNAPSHOT',{'WXYZEFGH':captured},[('copy',0,8)])])
 add('if',[('if',"IF FLAG = 'A'"),('a',"MOVE 'ABCDEFGH' TO SNAPSHOT"),'ELSE',('b',"MOVE 'IJKLMNOP' TO SNAPSHOT"),'END-IF.',('copy','MOVE SNAPSHOT TO RAW-AREA.'),prefix,call,end],
  [expectation('RAW-TEXT',{text:[fragment(0,4,'prefix'),fragment(4,8,producer,'SNAPSHOT',captures=['copy'])] for text,producer in [('WXYZEFGH','a'),('WXYZMNOP','b')]},[('prefix',0,4),('copy',4,8)])],control=['IF'])
 add('evaluate',[old,('evaluate','EVALUATE FLAG'),("a","WHEN 'A' MOVE 'WXYZ' TO PREFIX-PART"),('b',"WHEN OTHER MOVE 'BCDE' TO PREFIX-PART"),'END-EVALUATE.',call,end],
  [expectation('RAW-TEXT',{text:[fragment(0,4,producer),fragment(4,8,'old')] for text,producer in [('WXYZEFGH','a'),('BCDEEFGH','b')]},[('a',0,4),('b',0,4),('old',4,8)])],control=['EVALUATE'])
 add('goto',[old,('goto','GO TO PATCH-PREFIX.'),('dead',"MOVE 'XXXXXXXX' TO RAW-AREA."),'PATCH-PREFIX.',prefix,call,end],[expectation('RAW-TEXT',{'WXYZEFGH':partial},[('prefix',0,4),('old',4,8)])],control=['GO_TO'])
 add('perform',[('old',"MOVE 'ABCDEFGH' TO SOURCE-TEXT."),('perform','PERFORM COPY-TEXT.'),('late',"MOVE 'XXXXXXXX' TO SOURCE-TEXT."),('call','CALL SNAPSHOT.'),end,'COPY-TEXT.',('body','MOVE SOURCE-TEXT TO SNAPSHOT.')],
  [expectation('SNAPSHOT',{'ABCDEFGH':[fragment(0,8,'old','SOURCE-TEXT',captures=['body'])]},[('body',0,8)])],control=['PERFORM'])
 add('perform-twice',['MAIN-PARAGRAPH.',('old',"MOVE 'ABCDEFGH' TO SOURCE-TEXT."),('perform-a','PERFORM COPY-TEXT.'),('save','MOVE SNAPSHOT TO SAVED-TEXT.'),('new',"MOVE 'WXYZEFGH' TO SOURCE-TEXT."),('perform-b','PERFORM COPY-TEXT.'),('call','CALL SAVED-TEXT.'),end,'COPY-TEXT.',('body','MOVE SOURCE-TEXT TO SNAPSHOT.')],
  [expectation('SAVED-TEXT',{'ABCDEFGH':[fragment(0,8,'old','SOURCE-TEXT',captures=['body','save'])]},[('save',0,8)]),expectation('SNAPSHOT',{'WXYZEFGH':[fragment(0,8,'new','SOURCE-TEXT',captures=['body'])]},[('body',0,8)])],control=['PERFORM','PERFORM'])
 for times in (1,3):
  add('times-'+str(times),['MAIN-PARAGRAPH.',('old',"MOVE 'ABCDEFGH' TO SOURCE-TEXT."),('perform','PERFORM COPY-TEXT '+str(times)+' TIMES.'),('call','CALL SNAPSHOT.'),end,'COPY-TEXT.',('body','MOVE SOURCE-TEXT TO SNAPSHOT.')],
   [expectation('SNAPSHOT',{'ABCDEFGH':[fragment(0,8,'old','SOURCE-TEXT',captures=['body'])]},[('body',0,8)])],control=['PERFORM_PROCEDURE'])
 add('unknown',[call,end],[expectation('RAW-TEXT',{},[('ENTRY_UNKNOWN',0,8)],True)])
 # Explicitly unsupported source proofs must keep open effects/control. Exact
 # candidates are deliberately not used as an oracle for an open source region.
 add('if-partial',[old,('if',"IF FLAG = 'A'"),('prefix',"MOVE 'WXYZ' TO PREFIX-PART"),'ELSE',('other',"MOVE 'BCDE' TO PREFIX-PART"),'END-IF.',call,end],
  [expectation('RAW-TEXT',{text:[fragment(0,4,producer),fragment(4,8,'old')] for text,producer in [('WXYZEFGH','prefix'),('BCDEEFGH','other')]},[('prefix',0,4),('other',0,4),('old',4,8)])],control=['IF'])
 add('fallback-if',[old,('if',"IF FLAG > 'A'"),('prefix',"MOVE 'WXYZ' TO PREFIX-PART"),'ELSE',('other',"MOVE 'BCDE' TO PREFIX-PART"),'END-IF.',call,end],[],fallback=True)
 add('fallback-perform',['MAIN-PARAGRAPH.',old,('perform','PERFORM PATCH-PREFIX.'),call,end,'PATCH-PREFIX.',prefix],[],fallback=True)
 return cases
