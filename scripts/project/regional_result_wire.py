#!/usr/bin/env python3
"""Independent closed reader for regional-analysis-result 1.0/1.1; no Java/domain imports."""
from __future__ import annotations
import argparse,json,re
from pathlib import Path
from result_wire import WireError,require,fields,load,identity,distinct,token

DOMAINS=set('publication unit entry label object operation operand storage origin premise uncertainty'.split())
REASONS=set('CONTEXT_NOT_SELECTED UNKNOWN_OPERATION FOREIGN_UNIT AFTER_TERMINATOR OUTCOME_UNAVAILABLE UNSUPPORTED_SUBJECT'.split())
EVENTS=set('ENTRY_UNKNOWN INITIAL_CONDITION ENTRY_PRESERVE ENTRY_UNINITIALIZED ENTRY_PARAMETER ENTRY_EXTERNAL ASSIGN COPY UNKNOWN_WRITE'.split())

def string(v): require(isinstance(v,str),'string required')
def boolean(v): require(type(v) is bool,'boolean required')
def decimal(v):
    require(isinstance(v,str) and re.fullmatch(r'0|[1-9][0-9]*',v) is not None,'canonical decimal required');return int(v)
def strings(v):
    distinct(v,'strings')
    for x in v:string(x)
def outcome(v):
    if v is None:return
    require(isinstance(v,dict) and v.get('kind') in {'normal','exception','other-exception','halt','diverge'},'outcome kind')
    fields(v,'kind tag' if v['kind']=='exception' else 'kind','outcome')
    if v['kind']=='exception':string(v['tag'])
def extent(v):
    fields(v,'unit start end','range');require(v['unit']=='OCTET','range unit');a=decimal(v['start']);b=None if v['end'] is None else decimal(v['end']);require(b is None or a<=b,'range ordering');return a,b
def contains(a,b):return a[0]<=b[0] and (a[1] is None or b[1] is not None and b[1]<=a[1])

class Reader:
    def __init__(self,r):self.r=r;self.pub=identity(r['publicationId'],'publication');self.ids=set();self.bases={};self.entries=set()
    def ref(self,v,domain=None,check=True):
        require(isinstance(v,dict) and v.get('domain') in DOMAINS,'ID domain');identity(v,domain or v['domain'],self.pub if check else None)
        if check:require(token(v) in self.ids,'dangling reference')
    def refs(self,items,domain=None):
        distinct(items,'refs')
        for v in items:self.ref(v,domain)
    def point(self,p,check=True):
        fields(p,'position entryId operationId outcome','point');self.ref(p['entryId'],'entry',check)
        require(p['position'] in {'ENTRY','BEFORE','AFTER','OUTCOME'},'point position');outcome(p['outcome'])
        if check:require(token(p['entryId']) in self.entries,'context not selected')
        if p['position']=='ENTRY':require(p['operationId'] is None and p['outcome'] is None,'entry point shape')
        else:
            self.ref(p['operationId'],'operation',check)
            if check:require(p['operationId']['unit']==p['entryId']['unit'],'foreign operation unit')
        if p['position']=='BEFORE':require(p['outcome'] is None,'before outcome')
        if p['position']=='AFTER':require(p['outcome']=={'kind':'normal'},'after outcome')
    def type(self,t,check=True):
        require(isinstance(t,dict) and t.get('kind') in {'UNKNOWN','BUILTIN','EXTENSION','LABELS'},'type kind');k=t['kind']
        fields(t,{'UNKNOWN':'kind uncertainty','BUILTIN':'kind name','EXTENSION':'kind name version','LABELS':'kind unitId labels'}[k],'type')
        if k=='UNKNOWN':self.ref(t['uncertainty'],'uncertainty',check)
        if k=='BUILTIN':require(t['name'] in {'INT','DECIMAL','BOOL','TEXT','BYTES'},'builtin type')
        if k=='EXTENSION':string(t['name']);string(t['version'])
        if k=='LABELS':
            self.ref(t['unitId'],'unit',check);distinct(t['labels'],'labels');require(t['labels'],'empty label universe')
            for label in t['labels']:self.ref(label,'label',check);require(label['unit']==t['unitId']['localId'],'label owner')
    def codec(self,c,check=True):
        require(isinstance(c,dict) and c.get('kind') in {'IDENTITY_BYTES','ASCII_TEXT','BINARY','EXTENSION','UNKNOWN'},'codec kind');k=c['kind']
        fields(c,{'IDENTITY_BYTES':'kind','ASCII_TEXT':'kind','BINARY':'kind signed width order','EXTENSION':'kind name version logicalType','UNKNOWN':'kind reason logicalType'}[k],'codec')
        if k=='BINARY':boolean(c['signed']);require(decimal(c['width'])>0 and decimal(c['width'])%8==0,'binary width');require(c['order'] in {'LITTLE','BIG'},'byte order')
        if k=='EXTENSION':string(c['name']);string(c['version']);self.type(c['logicalType'],check)
        if k=='UNKNOWN':self.ref(c['reason'],'uncertainty',check);self.type(c['logicalType'],check)
    def subject(self,s,check=True):
        require(isinstance(s,dict) and s.get('kind') in {'NAMED_OBJECT','PHYSICAL_RANGE','PLACE_OCCURRENCE'},'subject kind')
        if s['kind']=='PLACE_OCCURRENCE':
            require(self.r['version']=='1.1.0','place occurrence requires 1.1');fields(s,'kind operandId','subject');self.ref(s['operandId'],'operand',check)
        elif s['kind']=='NAMED_OBJECT':fields(s,'kind objectId','subject');self.ref(s['objectId'],'object',check)
        else:
            fields(s,'kind storageId range codec','subject');self.ref(s['storageId'],'storage',check);self.codec(s['codec'],check);r=extent(s['range'])
            if check:
                b=self.bases[token(s['storageId'])];require(b['kind']=='REGION' and contains((0,None if b['extent'] is None else decimal(b['extent'])),r),'subject bounds')
    def location(self,l):
        fields(l,'storageId activation kind range','location');self.ref(l['storageId'],'storage');b=self.bases[token(l['storageId'])]
        if b['lifetime']=='ACTIVATION':self.ref(l['activation'],'entry');require(l['activation']['unit']==b['owner']['localId'],'activation owner')
        else:require(l['activation'] is None,'nonactivation context')
        if b['kind']=='CELL':require(l['kind']=='WHOLE_CELL' and l['range'] is None,'logical cell range')
        else:require(l['kind']=='BYTE_RANGE' and contains((0,None if b['extent'] is None else decimal(b['extent'])),extent(l['range'])),'physical range bounds')
    def interpretation(self,i):
        fields(i,'location codec','interpretation');self.location(i['location'])
        if i['location']['kind']=='WHOLE_CELL':require(i['codec'] is None,'logical interpretation codec')
        else:self.codec(i['codec'])
    def event(self,e,entry):
        fields(e,'entryId operationId destination slot outcome storageId kind unknown origin premiseRefs uncertaintyRefs reasons','event')
        require(e['entryId']==entry,'event Entry');self.ref(entry,'entry');self.ref(e['storageId'],'storage');self.ref(e['origin'],'origin');self.refs(e['premiseRefs'],'premise');self.refs(e['uncertaintyRefs'],'uncertainty');strings(e['reasons']);boolean(e['unknown']);outcome(e['outcome'])
        require(e['kind'] in EVENTS and type(e['slot']) is int,'event kind/slot')
        if e['kind']=='ENTRY_UNKNOWN':require(e['slot']==-1 and e['operationId'] is None and e['destination'] is None and e['outcome'] is None and e['unknown'],'entry unknown shape')
        else:
            require(e['slot']>=0,'write slot')
            if e['operationId'] is not None:self.ref(e['operationId'],'operation');require(e['operationId']['unit']==entry['unit'],'event operation owner')
            else:require(e['kind'] in {'INITIAL_CONDITION','ENTRY_PRESERVE','ENTRY_UNINITIALIZED','ENTRY_PARAMETER','ENTRY_EXTERNAL'} and e['outcome'] is None,'initial event shape')
            if e['destination'] is not None:
                self.ref(e['destination'],'operand');require(e['destination']['owner']==(e['operationId'] or entry),'destination occurrence owner')
    def contribution(self,l,e):self.location(l);require(l['storageId']==e['storageId'],'contribution event storage')
    def sublocation(self,outer,inner):
        require(outer['storageId']==inner['storageId'] and outer['activation']==inner['activation'] and outer['kind']==inner['kind'],'contribution location')
        if outer['range'] is not None:require(contains(extent(outer['range']),extent(inner['range'])),'contribution containment')
    def rd(self,f,entry):
        fields(f,'reachability unknownRemainder resolutionRemainder sourceUnknownRemainder definitions premiseRefs provenanceRefs uncertaintyRefs','RD')
        require(f['reachability'] in {'REACHABLE','UNREACHABLE_IN_MODEL'},'RD reachability');boolean(f['resolutionRemainder']);boolean(f['sourceUnknownRemainder']);self.refs(f['premiseRefs'],'premise');self.refs(f['provenanceRefs'],'origin');self.refs(f['uncertaintyRefs'],'uncertainty');distinct(f['definitions'],'definitions')
        if f['reachability']=='UNREACHABLE_IN_MODEL':require(f['unknownRemainder'] is None and not f['definitions'],'unreachable RD')
        else:boolean(f['unknownRemainder'])
        for d in f['definitions']:
            fields(d,'definition contributedRanges','RD contribution');self.event(d['definition'],entry);distinct(d['contributedRanges'],'RD ranges');require(d['contributedRanges'],'empty RD contribution')
            for l in d['contributedRanges']:self.contribution(l,d['definition'])
    def fragment(self,f,entry):
        fields(f,'location kind bytes producer unknownWriter captures sourceGaps modelReasons','fragment');self.location(f['location']);strings(f['modelReasons']);k=f['kind']
        require(k in {'KNOWN_BYTES','UNKNOWN_BYTES','LOGICAL_VALUE','UNKNOWN_LOGICAL','LOGICAL_CAPTURE'},'fragment kind')
        require((f['location']['kind']=='BYTE_RANGE')==(k in {'KNOWN_BYTES','UNKNOWN_BYTES'}),'fragment location kind')
        if f['bytes'] is not None:
            require(isinstance(f['bytes'],list) and all(type(b) is int and 0<=b<=255 for b in f['bytes']),'octets')
            require(k in {'KNOWN_BYTES','LOGICAL_CAPTURE'},'bytes kind')
            if k=='KNOWN_BYTES':a,b=extent(f['location']['range']);require(b is not None and len(f['bytes'])==b-a,'byte count')
        else:require(k not in {'KNOWN_BYTES','LOGICAL_CAPTURE'},'missing bytes')
        if f['producer'] is not None:
            p=f['producer'];fields(p,'definition contributedRange','producer');self.event(p['definition'],entry);self.contribution(p['contributedRange'],p['definition']);require(p['definition']['kind'] in {'ASSIGN','INITIAL_CONDITION'} and not p['definition']['unknown'],'literal producer')
        if k in {'KNOWN_BYTES','LOGICAL_VALUE','LOGICAL_CAPTURE'}:require(f['producer'] is not None and f['unknownWriter'] is None,'known producer shape')
        if f['unknownWriter'] is not None:self.event(f['unknownWriter'],entry);require(f['producer'] is None,'producer vs unknown writer')
        distinct(f['captures'],'captures')
        for c in f['captures']:
            fields(c,'definition before sourceRange destinationRange sourceContribution destinationContribution','capture');self.event(c['definition'],entry);self.point(c['before']);require(c['before']['entryId']==entry and c['before']['position']=='BEFORE' and c['before']['operationId']==c['definition']['operationId'],'capture instant')
            require(c['definition']['kind'] in {'COPY','ASSIGN'},'capture event kind')
            for name in ('sourceRange','destinationRange','sourceContribution','destinationContribution'):self.location(c[name])
            self.sublocation(c['sourceRange'],c['sourceContribution']);self.sublocation(c['destinationRange'],c['destinationContribution']);self.contribution(c['destinationRange'],c['definition'])
            if c['definition']['kind']=='COPY':
                require(c['sourceRange']['kind']==c['destinationRange']['kind']=='BYTE_RANGE','physical copy')
                s0,s1=extent(c['sourceContribution']['range']);d0,d1=extent(c['destinationContribution']['range']);a0,a1=extent(c['sourceRange']['range']);b0,b1=extent(c['destinationRange']['range'])
                require(None not in (s1,d1,a1,b1) and s1-s0==d1-d0 and a1-a0==b1-b0 and s0-a0==d0-b0,'copy contribution displacement')
        if f['producer'] is not None and f['producer']['contributedRange']!=f['location']:
            require(f['captures'],'relocated bytes need capture')
        if f['captures']:
            require(any(c['destinationContribution']['storageId']==f['location']['storageId'] and c['destinationContribution']['kind']==f['location']['kind'] and (f['location']['range'] is None or contains(extent(c['destinationContribution']['range']),extent(f['location']['range']))) for c in f['captures']),'capture does not contribute to observed location')
        distinct(f['sourceGaps'],'source gaps')
        for g in f['sourceGaps']:fields(g,'affectedLocation origin uncertaintyRefs','source gap');self.location(g['affectedLocation']);self.ref(g['origin'],'origin');self.refs(g['uncertaintyRefs'],'uncertainty')
    def value(self,f,entry):
        fields(f,'reachability interpretations candidates modelValueRemainder sourceUnknownRemainder effectiveUnknownRemainder candidateSupports premiseRefs evidenceRefs provenanceRefs modelReasons alternatives','value')
        require(f['reachability'] in {'REACHABLE','UNREACHABLE_IN_MODEL'},'value reachability');boolean(f['sourceUnknownRemainder']);boolean(f['effectiveUnknownRemainder']);strings(f['modelReasons']);self.refs(f['premiseRefs'],'premise');self.refs(f['evidenceRefs']);self.refs(f['provenanceRefs'],'origin')
        interpretations=distinct(f['interpretations'],'interpretations')
        for i in f['interpretations']:self.interpretation(i)
        if f['reachability']=='UNREACHABLE_IN_MODEL':require(f['candidates'] is None and f['modelValueRemainder'] is None and not f['candidateSupports'] and not f['alternatives'],'unreachable value')
        else:
            strings(f['candidates']);boolean(f['modelValueRemainder']);require(f['candidates'] or f['modelValueRemainder'],'empty closed reached')
        require(f['effectiveUnknownRemainder']==(f['modelValueRemainder'] is True or f['sourceUnknownRemainder']),'effective remainder')
        distinct(f['alternatives'],'alternatives');known=set();support={}
        for a in f['alternatives']:
            fields(a,'interpretation candidate fragments','alternative');require(token(a['interpretation']) in interpretations,'foreign interpretation');distinct(a['fragments'],'fragments')
            for fragment in a['fragments']:self.fragment(fragment,entry);self.sublocation(a['interpretation']['location'],fragment['location'])
            loc=a['interpretation']['location']
            if loc['kind']=='BYTE_RANGE':
                expected=extent(loc['range'])[0]
                for part in sorted(a['fragments'],key=lambda f:extent(f['location']['range'])[0]):
                    left,right=extent(part['location']['range']);require(left==expected and left!=right,'fragment coverage');expected=right
                require(expected==extent(loc['range'])[1],'missing fragment')
            if a['candidate'] is not None:
                string(a['candidate']);known.add(a['candidate']);require(all(p['kind'] not in {'UNKNOWN_BYTES','UNKNOWN_LOGICAL'} for p in a['fragments']),'candidate from unknown fragment')
                if loc['kind']=='BYTE_RANGE':
                    c=a['interpretation']['codec'];require(c['kind'] in {'ASCII_TEXT','EXTENSION'},'candidate for uninterpreted codec')
                    if c['kind']=='EXTENSION':require(c['name']=='text.ebcdic.ibm1047' and c['version']=='1' and c['logicalType']=={'kind':'BUILTIN','name':'TEXT'},'uninterpreted extension candidate')
                    if c['kind']=='ASCII_TEXT':
                        octets=[b for p in sorted(a['fragments'],key=lambda f:extent(f['location']['range'])[0]) for b in p['bytes']];require(all(b<128 for b in octets) and bytes(octets).decode('ascii')==a['candidate'],'independent ASCII composition')
                for p in a['fragments']:
                    e=p['producer']['definition'];support.setdefault(a['candidate'],set()).add(token({'evidence':e['operationId'] or e['destination'],'origin':e['origin'],'premiseRefs':e['premiseRefs']}))
            else:require(f['modelValueRemainder'],'unknown alternative without remainder')
        for a in f['alternatives']:
            for fragment in a['fragments']:
                if fragment['sourceGaps']:require(f['sourceUnknownRemainder'],'missing captured source remainder')
                for capture in fragment['captures']:require(token(capture['definition']['operationId']) in {token(x) for x in f['evidenceRefs']},'missing capture evidence')
        require(known==set(f['candidates'] or []),'candidate alternatives mismatch');distinct(f['candidateSupports'],'candidate supports');seen=set()
        for s in f['candidateSupports']:
            fields(s,'candidate producers','candidate support');require(s['candidate'] in known and s['candidate'] not in seen,'candidate support key');seen.add(s['candidate']);distinct(s['producers'],'producers')
            for p in s['producers']:fields(p,'evidence origin premiseRefs','support');self.ref(p['evidence']);self.ref(p['origin'],'origin');self.refs(p['premiseRefs'],'premise');require(token(p['evidence']) in {token(x) for x in f['evidenceRefs']},'missing aggregate evidence')
            require({token(p) for p in s['producers']}==support.get(s['candidate'],set()),'candidate support completeness')
        require(seen==known,'missing candidate supports')
    def validate(self):
        r=self.r;require(r['schema']=='regional-analysis-result' and r['version'] in {'1.0.0','1.1.0'} and r['profile']=='regional-text-images@2','schema/version/profile');string(r['resultId']);require(r['resultId'] and r['status']=='COMPLETE' and r['pathWitness']=='NOT_PROVIDED' and r['referenceAuthority']=='VALIDATED_AIR_PUBLICATION','result status/authority')
        inv=r['inventory'];fields(inv,'ids storages scopes','inventory');self.ids=distinct(inv['ids'],'inventory IDs')
        for i in inv['ids']:self.ref(i)
        self.ref(r['publicationId'],'publication')
        for i in inv['ids']:
            if 'unit' in i:self.ref({'domain':'unit','localId':i['unit'],'publication':self.pub},'unit')
            if 'owner' in i:self.ref(i['owner'])
        distinct(inv['storages'],'storages')
        for b in inv['storages']:
            fields(b,'storageId owner lifetime visibility origin kind extent extentUnknown','storage');self.ref(b['storageId'],'storage');self.ref(b['origin'],'origin');require(b['kind'] in {'REGION','CELL'} and b['lifetime'] in {'ACTIVATION','PERSISTENT','EXTERNAL'} and b['visibility'] in {'PRIVATE','SHARED','UNKNOWN'},'storage discriminants')
            if b['owner'] is not None:self.ref(b['owner'],'unit')
            require(b['lifetime']!='ACTIVATION' or b['owner'] is not None,'activation requires owner')
            if b['kind']=='CELL':require(b['extent'] is None and b['extentUnknown'] is None,'cell extent')
            else:
                require((b['extent'] is None)!=(b['extentUnknown'] is None),'region extent xor')
                if b['extent'] is not None:decimal(b['extent'])
                else:self.ref(b['extentUnknown'],'uncertainty')
            key=token(b['storageId']);require(key not in self.bases,'duplicate base');self.bases[key]=b
        require(set(self.bases)=={token(i) for i in inv['ids'] if i['domain']=='storage'},'storage inventory completeness')
        for s in inv['scopes']:
            fields(s,'entryId publicationInventory unitInventory publicationUncertainties unitUncertainties entryUncertainties','source scope');self.ref(s['entryId'],'entry');key=token(s['entryId']);require(key not in self.entries,'duplicate scope');self.entries.add(key)
            for k in ('publicationInventory','unitInventory'):require(s[k] in {'COMPLETE','PARTIAL','UNAVAILABLE'},'inventory status')
            for k in ('publicationUncertainties','unitUncertainties','entryUncertainties'):self.refs(s[k],'uncertainty')
        queries=set()
        for o in r['observations']:
            fields(o,'point subject rd values','observation');q=token([o['point'],o['subject']]);require(q not in queries,'duplicate logical query');queries.add(q)
            for name in ('rd','values'):
                v=o[name];fields(v,'status reason fact','observation product');require(v['status'] in {'VALUE','UNSUPPORTED_POINT'},'query status');accepted=v['status']=='VALUE';self.point(o['point'],accepted);self.subject(o['subject'],accepted)
                if accepted:
                    require(v['reason'] is None and v['fact'] is not None,'accepted shape');(self.rd if name=='rd' else self.value)(v['fact'],o['point']['entryId'])
                else:require(v['reason'] in REASONS and v['fact'] is None,'refused shape')
            if o['values']['fact'] is not None and o['subject']['kind']=='PHYSICAL_RANGE':
                for i in o['values']['fact']['interpretations']:require(i['codec']==o['subject']['codec'] and i['location']['storageId']==o['subject']['storageId'] and i['location']['range']==o['subject']['range'],'physical query interpretation')
            require(o['rd']['status']==o['values']['status'] and o['rd']['reason']==o['values']['reason'],'shared query admission')
            if o['rd']['fact'] is not None:require(o['rd']['fact']['reachability']==o['values']['fact']['reachability'],'shared reachability')
        fields(r['statistics'],'composition rd values rdObservation valueObservation','statistics')
        for m in r['statistics'].values():require(isinstance(m,dict) and all(isinstance(k,str) and type(v) is int and v>=0 for k,v in m.items()),'metrics')
        return r

def validate(r):
    fields(r,'schema version resultId publicationId profile status pathWitness referenceAuthority inventory observations statistics','regional result');return Reader(r).validate()
def read(path):return validate(load(path))
def canonical(r):return (json.dumps(validate(r),ensure_ascii=False,sort_keys=True,separators=(',',':'))+'\n').encode('utf-8')
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('result');parser.add_argument('--roundtrip');args=parser.parse_args();r=read(args.result)
    if args.roundtrip:Path(args.roundtrip).write_bytes(canonical(r))
    print(json.dumps({'regionalWire':'PASS','observations':len(r['observations'])}))
