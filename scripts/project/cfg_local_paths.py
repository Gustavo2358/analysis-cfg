"""Independent test traversal of published CFG rules; never flattens local returns."""
import collections,json

def vertex(ref):return (ref['publication'],str(ref['ordinal']))
def key(ref):return json.dumps(ref,sort_keys=True,separators=(',',':'))
class Paths:
    def __init__(self,cfg):
        self.ordinary=collections.defaultdict(list)
        for e in cfg['transitions']:self.ordinary[vertex(e['from'])].append(vertex(e['to']))
        self.rules={vertex(r['source']):r for r in cfg.get('localControl',[])}
        self.exits={vertex(n['id']) for n in cfg['nodes'] if n['kind'] not in {'SEQUENCE','ENTRY'}}
    def successors(self,at,stack):
        r=self.rules.get(at)
        if not r:return [(d,() if d in self.exits else stack) for d in self.ordinary[at]]
        kind=r['kind']
        if kind=='LOCAL_INVOKE':
            ident=key(r['operation'])
            if any(f[0]==ident for f in stack):raise ValueError('recursive test traversal requires another oracle')
            return [(vertex(r['entry']),stack+((ident,vertex(r['resume']),tuple(key(p) for p in r['ports'])),))]
        if kind=='LOCAL_BOUNDARY':return [(stack[-1][1],stack[:-1])] if stack and key(r['port']) in stack[-1][2] else [(vertex(r['defaultDestination']),stack)]
        if kind=='LOCAL_RESUME':return [(stack[-1][1],stack[:-1])] if stack else [(vertex(r['invalidExit']),())]
        if kind=='LOCAL_UNWIND':
            count=int(r['count']);return [(vertex(r['invalidExit']),())] if count>len(stack) else [(vertex(r['destination']),stack[:len(stack)-count])]
        raise ValueError('unknown local rule')
    def reachable(self,starts,blocked=frozenset()):
        seen=set();todo=collections.deque((s,()) for s in starts)
        while todo:
            at,stack=todo.popleft()
            if at in blocked or (at,stack) in seen:continue
            seen.add((at,stack));todo.extend(self.successors(at,stack))
        return {at for at,stack in seen}
