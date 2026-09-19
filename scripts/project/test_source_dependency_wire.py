#!/usr/bin/env python3
"""Closed source-dependencies 2.4 contract; actual CLI products supplied locally."""
import copy
import sys
from pathlib import Path
from dependency_wire import read, validate


def exercise(path):
    document=read(path)
    for mutation in ('usage','missing','unavailable','unknown','kind','authority','no-support','origin','owner','resolution','count','remainder','old-version'):
        d=copy.deepcopy(document); value=d['sourceDependencies']; fact=value['dependencies'][0]; support=fact['supports'][0]
        if mutation=='usage':support['operation']='SELECT';support['access']='WRITE'
        elif mutation=='missing': del d['sourceDependencies']
        elif mutation=='unavailable':value['available']=False;value['remainder']=True;d['analysisStatus']='PARTIAL'
        elif mutation=='unknown':value['unknown']=True
        elif mutation=='kind':fact['kind']='TABLE'
        elif mutation=='authority':support['classificationAuthority']='INFERRED_BY_PREFIX'
        elif mutation=='no-support':fact['supports']=[]
        elif mutation=='origin':support['origin']['localId']='missing'
        elif mutation=='owner':support['sourceOwner']['localId']='missing'
        elif mutation=='resolution':support['resolution']='GUESSED'
        elif mutation=='count':value['occurrences']+=1
        elif mutation=='remainder':fact['remainder']=not fact['remainder']
        elif mutation=='old-version':d['version']='2.3.0'
        try:validate(d)
        except (ValueError,KeyError,TypeError):pass
        else:raise AssertionError('accepted malformed source product: '+mutation)
    print('PASS: W3 writer/reader and 13 negative wire cases')


if __name__=='__main__':
    exercise(Path(sys.argv[1]))
