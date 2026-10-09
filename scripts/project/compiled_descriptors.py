"""Shared public class descriptor capture; architecture inventories remain exact."""
import re

def public_descriptors(paths,classpath,capture,batch_size=64):
    paths=list(paths)
    if type(batch_size)!=int or not 1<=batch_size<=64:raise ValueError('descriptor batch size must be 1..64')
    if not paths or len(set(paths))!=len(paths):raise ValueError('empty or duplicate class inventory')
    for path in paths:
        if not path.endswith('.class') or any(not part.replace('$','_').isidentifier() for part in path[:-6].split('/')):
            raise ValueError('invalid relative binary class path: '+path)
    descriptors={}
    for first in range(0,len(paths),batch_size):
        chunk=paths[first:first+batch_size];names=[p[:-6].replace('/','.') for p in chunk]
        # This flag applies only to read-only javap processes, never Maven, CLI or application JVMs.
        output=capture(['javap','-J-XX:-UsePerfData','-classpath',classpath,'-public','-s',*names])
        lines=output.splitlines(keepends=True)
        starts=[i for i,line in enumerate(lines) if line.startswith('Compiled from "')]
        if len(starts)!=len(chunk) or not starts or starts[0]!=0:raise ValueError('javap batch block inventory mismatch')
        for path,name,start,end in zip(chunk,names,starts,starts[1:]+[len(lines)]):
            header=lines[start+1] if start+1<end else ''
            declared=re.search(r'\b(?:class|interface)\s+([^\s<{]+)',header)
            if declared is None or declared.group(1)!=name:raise ValueError('javap batch declaration/order mismatch: '+name)
            descriptors[path]=''.join(lines[start:end]) # No stripping, normalization or regenerated surface.
    return descriptors
