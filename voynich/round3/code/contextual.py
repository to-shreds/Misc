"""Bounded, invertible two-state substitution. Candidate output is not a translation.

Two alphabet permutations may differ at no more than four code positions. State
is observable from word position or the previous written word's final symbol.
This tests a narrow contextual family, not general medieval writing systems.
"""
from __future__ import annotations
import sys, pathlib, collections, math, json
import numpy as np
from numba import njit
ROOT=pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'vendor'))
import solver as old

MODES=('static','previous_final','word_initial')

def state(mode: str, word_index: int, position: int, previous: str, trigger: str='y') -> int:
    if mode=='static': return 0
    if mode=='word_initial': return int(position==0)
    if mode=='previous_final': return int(word_index>0 and previous.endswith(trigger))
    raise ValueError(mode)

def event_streams(runs, alphabet, mode, trigger='y'):
    """Three leading spaces per uninterrupted run; spaces never change state."""
    idx={a:i for i,a in enumerate(alphabet)}; n=len(alphabet)
    streams=[]
    for run in runs:
        stream=[0,0,0];prev=''
        for wi,w in enumerate(run):
            for j,c in enumerate(w):stream.append(idx[c]+n*state(mode,wi,j,prev,trigger))
            stream.append(0);prev=''.join(w)
        streams.append(stream)
    return streams

def grams(runs,alphabet,mode,trigger='y'):
    counts=collections.Counter()
    for stream in event_streams(runs,alphabet,mode,trigger):
        counts.update(zip(stream,stream[1:],stream[2:],stream[3:]))
    if not counts: raise ValueError('Empty scored sample')
    return np.asarray(list(counts),dtype=np.int64),np.asarray(list(counts.values()),dtype=np.float64)

def penalty_table(n,max_differences=4):
    """Nats needed to select the relative permutation, conditional on its size.
    Not a complete MDL code: mode, language and size coding are not included.
    The explicit four-position cap is the primary complexity restriction.
    """
    table=np.zeros(n,dtype=np.float64);der=[1,0]
    for k in range(2,n):der.append((k-1)*(der[k-1]+der[k-2]))
    for k in range(1,n):
        table[k]=math.log(math.comb(n-1,k)*der[k]) if der[k] else 1e100
    return table

@njit(cache=True)
def mismatch(key,n):
    return np.sum(key[1:n]!=key[n+1:])

@njit(cache=True)
def search(gs,weights,logp,d,initial,flat,offsets,restarts,steps,seed,cap,penalties):
    np.random.seed(seed);n=len(initial)//2
    best=initial.copy();best_raw=old.score_key(gs,weights,best,logp,d)
    best_obj=best_raw-penalties[mismatch(best,n)];history=np.zeros((restarts,3))
    for rr in range(restarts):
        key=initial.copy()
        if rr:
            for _ in range(2+rr%6):
                a=np.random.randint(1,n);b=np.random.randint(1,n)
                for st in range(2):key[st*n+a],key[st*n+b]=key[st*n+b],key[st*n+a]
        raw=old.score_key(gs,weights,key,logp,d);diff=mismatch(key,n)
        obj=raw-penalties[diff]
        if obj>best_obj:best_obj=obj;best_raw=raw;best=key.copy()
        for step in range(steps):
            a=np.random.randint(1,n);b=np.random.randint(1,n)
            if a==b:continue
            which=2 if np.random.random()<.60 else np.random.randint(0,2)
            if which!=2 and diff>=cap and np.random.random()<.75:
                changed=np.where(key[1:n]!=key[n+1:])[0]+1
                if len(changed):a=changed[np.random.randint(len(changed))]
                if a==b:continue
            newdiff=diff
            if which!=2:
                k0a=key[a];k0b=key[b];k1a=key[n+a];k1b=key[n+b]
                newdiff-=int(k0a!=k1a)+int(k0b!=k1b)
                if which==0:newdiff+=int(k0b!=k1a)+int(k0a!=k1b)
                else:newdiff+=int(k0a!=k1b)+int(k0b!=k1a)
                if newdiff>cap:continue
            delta=0.
            lo,hi=offsets[a,b]
            for pp in range(lo,hi):
                g=flat[pp];oldi=0;newi=0
                for t in range(4):
                    event=gs[g,t];st=event//n;sym=event%n
                    v=key[event];nv=v
                    if which==2 or st==which:
                        if sym==a:nv=key[st*n+b]
                        elif sym==b:nv=key[st*n+a]
                    oldi=oldi*d+v;newi=newi*d+nv
                delta+=weights[g]*(logp[newi]-logp[oldi])
            dobj=delta+penalties[diff]-penalties[newdiff]
            temp=60.*(.004**(step/steps))
            if dobj>=0 or np.random.random()<math.exp(max(-700.,dobj/temp)):
                if which in (0,2):key[a],key[b]=key[b],key[a]
                if which in (1,2):key[n+a],key[n+b]=key[n+b],key[n+a]
                raw+=delta;obj+=dobj;diff=newdiff
                if obj>best_obj:best_obj=obj;best_raw=raw;best=key.copy()
        history[rr]=np.array([raw,obj,diff],dtype=np.float64)
    return best,best_raw,best_obj,history


@njit(cache=True)
def polish(gs,ws,logp,d,initial,cap,penalties,passes=4):
    """Direct 3-cycles can bridge a hard-cap barrier that pair moves cannot.
    Added after a known German control exposed this search failure, before any
    manuscript fit. Greedy legal two-/three-cycle sweeps, not exhaustive keys.
    """
    key=initial.copy();n=len(key)//2
    raw=old.score_key(gs,ws,key,logp,d);obj=raw-penalties[mismatch(key,n)]
    performed=0
    for sweep in range(passes):
        candidate=key.copy();best_raw=raw;best_obj=obj
        for st in range(2):
            off=st*n
            for a in range(1,n):
                for b in range(a+1,n):
                    ka=key[off+a];kb=key[off+b]
                    key[off+a]=kb;key[off+b]=ka
                    mm=mismatch(key,n)
                    if mm<=cap:
                        score=old.score_key(gs,ws,key,logp,d);val=score-penalties[mm]
                        if val>best_obj+1e-8:candidate=key.copy();best_obj=val;best_raw=score
                    key[off+a]=ka;key[off+b]=kb
                    for c in range(b+1,n):
                        kc=key[off+c]
                        for direction in range(2):
                            if direction==0:
                                key[off+a]=kb;key[off+b]=kc;key[off+c]=ka
                            else:
                                key[off+a]=kc;key[off+b]=ka;key[off+c]=kb
                            mm=mismatch(key,n)
                            if mm<=cap:
                                score=old.score_key(gs,ws,key,logp,d);val=score-penalties[mm]
                                if val>best_obj+1e-8:candidate=key.copy();best_obj=val;best_raw=score
                        key[off+a]=ka;key[off+b]=kb;key[off+c]=kc
        if best_obj<=obj+1e-8:break
        key=candidate;obj=best_obj;raw=best_raw;performed+=1
    return key,raw,obj,performed

def decode(runs,alphabet,plainalpha,key,mode,trigger='y'):
    n=len(alphabet);ci={x:i for i,x in enumerate(alphabet)};out=[]
    for run in runs:
        line=[];previous=''
        for wi,word in enumerate(run):
            line.append(''.join(plainalpha[key[ci[c]+n*state(mode,wi,j,previous,trigger)]] for j,c in enumerate(word)))
            previous=''.join(word)
        out.append(line)
    return out

def encode(plainruns,alphabet,plainalpha,key,mode,trigger='y'):
    """Forward encoder: state uses only already emitted text and current position."""
    n=len(alphabet);pa={c:i for i,c in enumerate(plainalpha)}
    inverse=[{key[st*n+i]:alphabet[i] for i in range(1,n)} for st in range(2)]
    out=[]
    for run in plainruns:
        line=[];prev=''
        for wi,w in enumerate(run):
            word=[]
            for j,c in enumerate(w):
                st=state(mode,wi,j,prev,trigger)
                if pa[c] not in inverse[st]: raise ValueError('Plaintext letter outside encoder alphabet')
                word.append(inverse[st][pa[c]])
            line.append(tuple(word));prev=''.join(word)
        out.append(line)
    return out

def evaluate(prepared,alphabet,plainalpha,key,mode,logp,trigger='y',vocab=None):
    gs,ww=grams(prepared,alphabet,mode,trigger)
    score=old.score_key(gs,ww,key,logp,len(plainalpha))
    out=decode(prepared,alphabet,plainalpha,key,mode,trigger)
    words=[w for r in out for w in r];long=[w for w in words if len(w)>=4]
    return {'bits_per_unit':float(-score/ww.sum()/math.log(2)),
      'scored_units':int(ww.sum()),'groups':len(words),
      'dictionary_coverage':float(sum(w in vocab for w in words)/max(1,len(words))) if vocab is not None else None,
      'long_dictionary_coverage':float(sum(w in vocab for w in long)/max(1,len(long))) if vocab is not None else None,
      'preview':out[:8]}

def prepare_dataset(trainruns,evalruns,lang,scheme):
    logp,pa,pt=old.language_model(lang);d=len(pa)
    freq=collections.Counter(c for run in trainruns for w in run for c in old.tokenize(w,scheme))
    chosen=[c for c,f in freq.most_common(d-1)]
    tr,ex,tot=old.prepare(trainruns,scheme,allowed=set(chosen))
    te,tex,ttot=old.prepare(evalruns,scheme,allowed=set(chosen))
    ca=[' ']+chosen+[f'<unused:{i}>' for i in range(d-1-len(chosen))]
    pf=collections.Counter(c for r in pt for w in r for c in w)
    init=np.asarray([0]+sorted(range(1,d),key=lambda i:-pf[pa[i]]),dtype=np.int64)
    stats={'train_groups':tot-ex,'train_excluded_groups':ex,'evaluation_groups':ttot-tex,
           'evaluation_excluded_groups':tex,'source_alphabet_size':len(freq),'retained_source_units':len(chosen)}
    return logp,pa,pt,ca,tr,te,init,stats

def fit_models(trainruns,devruns,lang,scheme='raw',trigger='y',seed=260927,restarts=6,steps=20000):
    logp,pa,pt,ca,tr,dev,initial,stats=prepare_dataset(trainruns,devruns,lang,scheme)
    d=len(pa);n=len(ca);vocab={w for r in pt for w in r}
    gs,ws=old.ciphergrams(tr,ca);flat,off=old.impact(gs,n)
    key0,raw,hist=old.anneal(gs,ws,logp,d,initial,flat,off,restarts,steps,seed)
    assert abs(raw-old.score_key(gs,ws,key0,logp,d))<.01
    base=np.concatenate([key0,key0]);pen=penalty_table(n);results={}
    for mode in MODES:
        if mode=='static':key=base;history=hist.tolist();obj=raw;rawscore=raw
        else:
            cg,cw=grams(tr,ca,mode,trigger);ff,oo=old.impact(cg%n,n)
            key,rawscore,obj,history=search(cg,cw,logp,d,base,ff,oo,restarts,steps,seed+MODES.index(mode),4,pen)
            key,rawscore,obj,polish_sweeps=polish(cg,cw,logp,d,key,4,pen)
            check=old.score_key(cg,cw,key,logp,d)
            assert abs(rawscore-check)<.01,(rawscore,check)
            assert mismatch(key,n)<=4
            assert abs(obj-(check-pen[mismatch(key,n)]))<.01
            history=history.tolist()
        for st in range(2):assert sorted(key[st*n:(st+1)*n].tolist())==list(range(d))
        results[mode]={'mode':mode,'trigger':trigger,'language':lang,'scheme':scheme,
          'source_alphabet':ca,'plaintext_alphabet':pa,'key':key.tolist(),
          'differences':int(mismatch(key,n)),'relative_key_cost_bits':float(pen[mismatch(key,n)]/math.log(2)),
          'train':evaluate(tr,ca,pa,key,mode,logp,trigger,vocab),
          'development':evaluate(dev,ca,pa,key,mode,logp,trigger,vocab),
          'optimization_history':history,'seed':seed,'restarts':restarts,'steps':steps,
          'coverage':stats,'post_annealing_cycle_polish_sweeps':int(polish_sweeps) if mode!='static' else 0,'candidate_is_translation':False}
    return results
