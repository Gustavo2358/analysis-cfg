#!/usr/bin/env python3
"""Independent source recipes and physical goldens for ST-W3, with no producer imports."""
def program(name,data,body):
    lines=['IDENTIFICATION DIVISION.','PROGRAM-ID. '+name+'.','DATA DIVISION.','WORKING-STORAGE SECTION.',*data,'PROCEDURE DIVISION.',*body,'GOBACK.']
    assert all(len(line)<=65 for line in lines),'fixed-format source line overflow'
    return ''.join('       '+line+'\n' for line in lines)

def node(parent,base,start,extent,group=False,filler=False,opaque=False):
    return dict(parent=parent,base=base,offset=start,extent=extent,kind='OPAQUE' if opaque else ('GROUP' if group else 'ELEMENTARY'),filler=filler)

def call(statement,node_index,text,producer,rd=None,model_open=False):
    return dict(statement=statement,node=node_index,text=text,producer=producer,rd=[producer] if rd is None else rd,modelOpen=model_open)

def assign(statement,base,start,text):
    return dict(statement=statement,base=base,offset=start,length=len(text),text=text)

def fixtures():
    cases={}
    def add(name,data,body,nodes,extents,calls,writes,**extra):
        cases[name]=dict(source=program(name.upper(),data,body),nodes=nodes,extents=extents,calls=calls,writes=writes,books={},cells=0,**extra)
    for name,area,target in [('group-child','WS-AREA','WS-PGM'),('renamed-group','RENAMED-AREA','RENAMED-TARGET')]:
        add(name,['01 '+area+'.','   05 '+target+' PIC X(8).'],["MOVE 'PGM00001' TO "+area+'.','CALL '+target+'.'],
            [node(None,0,0,8,True),node(0,0,0,8)],[8],[call(1,1,'PGM00001',0)],[assign(0,0,0,'PGM00001')])
    add('nested-filler',['01 WS-AREA.','   05 LEAD PIC X(4).','   05 FILLER PIC X(2).','   05 CHILD.','      10 WS-PGM PIC X(8).'],["MOVE 'ABCD++PGM00001' TO WS-AREA.",'CALL WS-PGM.'],
        [node(None,0,0,14,True),node(0,0,0,4),node(0,0,4,2,filler=True),node(0,0,6,8,True),node(3,0,6,8)],[14],[call(1,4,'PGM00001',0)],[assign(0,0,0,'ABCD++PGM00001')])
    add('copy-capture',['01 X PIC X(4).','01 Y PIC X(4).'],["MOVE 'ABCD' TO X.",'MOVE X TO Y.',"MOVE 'WXYZ' TO X.",'CALL Y.'],
        [node(None,0,0,4),node(None,1,0,4)],[4,4],[call(3,1,'ABCD',0,rd=[1])],[assign(0,0,0,'ABCD'),dict(statement=1,copy=True,base=1,offset=0,length=4,sourceBase=0,sourceOffset=0),assign(2,0,0,'WXYZ')])
    add('repeated-calls',['01 WS-AREA.','   05 WS-PGM PIC X(8).'],["MOVE 'PGM00001' TO WS-AREA.",'CALL WS-PGM.','CALL WS-PGM.'],
        [node(None,0,0,8,True),node(0,0,0,8)],[8],[call(1,1,'PGM00001',0),call(2,1,'PGM00001',0,rd=[0,1],model_open=True)],[assign(0,0,0,'PGM00001')])
    add('siblings-disjoint',['01 WS-AREA.','   05 FIRST-PGM PIC X(8).','   05 SECOND-PGM PIC X(8).'],["MOVE 'PGM00001' TO FIRST-PGM.","MOVE 'PGM00002' TO SECOND-PGM.",'CALL FIRST-PGM.',"MOVE 'PGM00002' TO SECOND-PGM.",'CALL SECOND-PGM.'],
        [node(None,0,0,16,True),node(0,0,0,8),node(0,0,8,8)],[16],[call(2,1,'PGM00001',0),call(4,2,'PGM00002',3)],
        [assign(0,0,0,'PGM00001'),assign(1,0,8,'PGM00002'),assign(3,0,8,'PGM00002')])
    for n in (1,2,5,40):
        data=[];body=[];nodes=[];calls=[];writes=[]
        for i in range(n):
            area='ROOT-'+str(i+1).zfill(4);text='P'+str(i+1).zfill(7);parent=len(nodes)
            data+=['01 '+area+'.','   05 WS-PGM PIC X(8).'];nodes +=[node(None,i,0,8,True),node(parent,i,0,8)]
            body +=["MOVE '"+text+"' TO "+area+'.','CALL WS-PGM OF '+area+'.'];writes.append(assign(2*i,i,0,text));calls.append(call(2*i+1,parent+1,text,2*i))
        add('same-names-'+str(n),data,body,nodes,[8]*n,calls,writes)
        data=['01 WS-AREA.','   05 FILLER PIC X(2).'];body=[];nodes=[node(None,0,0,9*n+1,True),node(0,0,0,2,filler=True)];calls=[];writes=[]
        for i in range(n):
            target='TARGET-'+str(i+1).zfill(4);text='P'+str(i+1).zfill(7);index=len(nodes)
            data.append('   05 '+target+' PIC X(8).');nodes.append(node(0,0,2+9*i,8))
            body +=["MOVE '"+text+"' TO "+target+'.','CALL '+target+'.'];writes.append(assign(2*i,0,2+9*i,text));calls.append(call(2*i+1,index,text,2*i))
            if i+1<n:data.append('   05 FILLER PIC X.');nodes.append(node(0,0,10+9*i,1,filler=True))
        add('children-'+str(n),data,body,nodes,[9*n+1],calls,writes)
    add('mixed-scalar',['01 WS-AREA.','   05 WS-PGM PIC X(8).','01 SCALAR-PGM PIC X(8).','01 COUNTER PIC 9.'],
        ["MOVE 'PGM00001' TO WS-AREA.",'CALL WS-PGM.',"MOVE 'PGM00002' TO SCALAR-PGM.",'CALL SCALAR-PGM.','GO TO FINISH DEPENDING ON COUNTER.','FINISH.'],
        [node(None,0,0,8,True),node(0,0,0,8),node(None,1,0,8),node(None,2,0,None,opaque=True)],[8,8,None],
        [call(1,1,'PGM00001',0),call(3,2,'PGM00002',2)],[assign(0,0,0,'PGM00001'),assign(2,1,0,'PGM00002')])
    # Numeric control is a logical Cell; its byte layout is outside the fixed DISPLAY text profile.
    cases['mixed-scalar']['cells']=1
    copybook=dict(cases['group-child']);copybook['source']=program('COPYBOOK-GROUP',['COPY AREADEF.'],["MOVE 'PGM00001' TO WS-AREA.",'CALL WS-PGM.']);copybook['books']={'AREADEF.cpy':'       01 WS-AREA.\n          05 WS-PGM PIC X(8).\n'};cases['copybook-group']=copybook
    unknown=program('UNKNOWN-PREFIX',['01 WS-AREA.','   05 VARIABLE-TABLE OCCURS 1 TO 4','      DEPENDING ON N.','      10 ITEM PIC X.','   05 WS-PGM PIC X(8).','01 N PIC 9.'],["MOVE 'PGM00001' TO WS-PGM.",'CALL WS-PGM.'])
    cases['unknown-prefix']=dict(source=unknown,books={},unknown=True)
    return cases

# Limited, independently specified alphabet needed by these source recipes, not a runtime codec.
def ibm_octets(text):
    mapping={' ':0x40,'+':0x4e}
    mapping.update({chr(ord('A')+i):0xc1+i for i in range(9)})
    mapping.update({chr(ord('J')+i):0xd1+i for i in range(9)})
    mapping.update({chr(ord('S')+i):0xe2+i for i in range(8)})
    mapping.update({str(i):0xf0+i for i in range(10)})
    return [mapping[c] for c in text]
