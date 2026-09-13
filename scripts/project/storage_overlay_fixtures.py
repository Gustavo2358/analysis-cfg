"""W4 physical goldens written independently of the source and AIR implementations."""
from storage_group_fixtures import program,node,call,assign

def fixtures():
    cases={}
    def add(name,data,body,nodes,extents,calls,writes,relations,**extra):
        cases[name]=dict(source=program(name.upper(),data,body),nodes=nodes,extents=extents,calls=calls,writes=writes,relations=relations,books={},cells=0,**extra)
    for name,raw,view,reverse in [('raw-view','RAW','WS-PGM',False),('view-raw','RAW','WS-PGM',True),('renamed-overlay','BUFFER-A','TARGET-B',False)]:
        add(name,['01 '+raw+' PIC X(8).','01 '+view+' REDEFINES '+raw+' PIC X(8).'],
            ["MOVE 'PGM00001' TO "+(view if reverse else raw)+'.','CALL '+(raw if reverse else view)+'.'],
            [node(None,0,0,8),node(None,0,0,8)],[8],[call(1,0 if reverse else 1,'PGM00001',0)],[assign(0,0,0,'PGM00001')],[(1,0)])
    add('nested-filler-overlay',['01 WS-AREA.','   05 PREFIX-AREA PIC X(2).','   05 RAW PIC X(4).','   05 FILLER REDEFINES RAW.','      10 WS-PGM PIC X(8).','   05 TAIL-AREA PIC X(2).'],
        ["MOVE '++PGM00001TT' TO WS-AREA.",'CALL WS-PGM.'],
        [node(None,0,0,12,True),node(0,0,0,2),node(0,0,2,4),node(0,0,2,8,True,True),node(3,0,2,8),node(0,0,10,2)],
        [12],[call(1,4,'PGM00001',0)],[assign(0,0,0,'++PGM00001TT')],[(3,2)])
    add('smaller-view',['01 RAW PIC X(8).','01 SHORT-VIEW REDEFINES RAW PIC X(4).'],
        ["MOVE 'ABCDEFGH' TO RAW.",'CALL SHORT-VIEW.'],[node(None,0,0,8),node(None,0,0,4)],[8],[call(1,1,'ABCD',0)],[assign(0,0,0,'ABCDEFGH')],[(1,0)])
    add('larger-view',['01 RAW PIC X(4).','01 WIDE-VIEW REDEFINES RAW PIC X(8).'],
        ["MOVE 'ABCDEFGH' TO WIDE-VIEW.",'CALL RAW.'],[node(None,0,0,4),node(None,0,0,8)],[8],[call(1,0,'ABCD',0)],[assign(0,0,0,'ABCDEFGH')],[(1,0)])
    add('overlay-disjoint-sibling',['01 WS-AREA.','   05 RAW PIC X(8).','   05 ALT-GROUP REDEFINES RAW.','      10 LEFT-HALF PIC X(4).','      10 RIGHT-HALF PIC X(4).','   05 WS-PGM PIC X(8).'],
        ["MOVE 'PGM00001' TO WS-PGM.","MOVE 'ABCD' TO LEFT-HALF.",'CALL WS-PGM.'],
        [node(None,0,0,16,True),node(0,0,0,8),node(0,0,0,8,True),node(2,0,0,4),node(2,0,4,4),node(0,0,8,8)],
        [16],[call(2,5,'PGM00001',0)],[assign(0,0,8,'PGM00001'),assign(1,0,0,'ABCD')],[(2,1)])
    add('overlay-disjoint-half',['01 RAW PIC X(8).','01 PARTS REDEFINES RAW.','   05 LEFT-HALF PIC X(4).','   05 RIGHT-HALF PIC X(4).'],
        ["MOVE 'ABCDEFGH' TO RAW.","MOVE 'WXYZ' TO LEFT-HALF.",'CALL RIGHT-HALF.'],
        [node(None,0,0,8),node(None,0,0,8,True),node(1,0,0,4),node(1,0,4,4)],
        [8],[call(2,3,'EFGH',0)],[assign(0,0,0,'ABCDEFGH'),assign(1,0,0,'WXYZ')],[(1,0)])
    add('descending-levels',['01 WS-AREA.','   05 RAW PIC X(4).','   04 WIDE-VIEW REDEFINES RAW PIC X(8).','   03 BIG-VIEW REDEFINES WIDE-VIEW PIC X(12).','   03 WS-PGM PIC X(8).'],
        ["MOVE 'PGM00001' TO WS-PGM.",'CALL WS-PGM.'],
        [node(None,0,0,20,True),node(0,0,0,4),node(0,0,0,8),node(0,0,0,12),node(0,0,12,8)],
        [20],[call(1,4,'PGM00001',0)],[assign(0,0,12,'PGM00001')],[(2,1),(3,2)])
    add('local-scalar-control',['01 RAW PIC X(8).','01 ALT-AREA REDEFINES RAW PIC X(8).','01 WS-PGM PIC X(8).','01 COUNTER PIC 9.'],
        ["MOVE 'PGM00001' TO WS-PGM.",'CALL WS-PGM.','GO TO FINISH DEPENDING ON COUNTER.','FINISH.'],
        [node(None,0,0,8),node(None,0,0,8),node(None,1,0,8),node(None,2,0,None,opaque=True)],
        [8,8,None],[call(1,2,'PGM00001',0)],[assign(0,1,0,'PGM00001')],[(1,0)])
    cases['local-scalar-control']['cells']=1
    for n in (1,2,5,40):
        data=[];body=[];nodes=[];calls=[];writes=[];relations=[]
        for i in range(n):
            root='ROOT-'+str(i+1).zfill(4);text='P'+str(i+1).zfill(7);index=len(nodes)
            data+=['01 '+root+'.','   05 RAW PIC X(8).','   05 WS-PGM REDEFINES RAW PIC X(8).']
            nodes +=[node(None,i,0,8,True),node(index,i,0,8),node(index,i,0,8)];relations.append((index+2,index+1))
            body +=["MOVE '"+text+"' TO RAW OF "+root+'.','CALL WS-PGM OF '+root+'.']
            writes.append(assign(2*i,i,0,text));calls.append(call(2*i+1,index+2,text,2*i))
        add('same-names-overlays-'+str(n),data,body,nodes,[8]*n,calls,writes,relations)
    copy=dict(cases['raw-view']);copy['source']=program('COPYBOOK-OVERLAY',['COPY AREADEF.'],["MOVE 'PGM00001' TO RAW.",'CALL WS-PGM.'])
    copy['books']={'AREADEF.cpy':'       01 RAW PIC X(8).\n       01 WS-PGM REDEFINES RAW PIC X(8).\n'};cases['copybook-overlay']=copy
    for name,data in [('missing-target',['01 RAW PIC X(8).','01 WS-PGM REDEFINES MISSING-ITEM PIC X(8).']),('unknown-overlay-extent',['01 WS-AREA.','   05 RAW PIC X(8).','   05 OPAQUE-VIEW REDEFINES RAW PIC 9.','   05 WS-PGM PIC X(8).'])]:
        cases[name]=dict(source=program(name.upper(),data,["MOVE 'PGM00001' TO WS-PGM.",'CALL WS-PGM.']),books={},unproved=True,missingRelation=name=='missing-target')
    return cases
