"""Post-primary structural extension: only canonically valid training keys.

MILP finds an injective alphabet assignment obeying every observed training
adjacency. A constrained swap search then improves the reference likelihood
without ever accepting an illegal training transition. This is a bounded repair,
not exhaustive cryptanalysis. All original primary results remain unchanged.
"""
from __future__ import annotations
import argparse,collections,concurrent.futures,datetime,itertools,json,math,pathlib,time
import numpy as np
from numba import njit
from scipy.optimize import milp,Bounds,LinearConstraint
from scipy.sparse import csc_matrix
import engine as e
import run as runner
R=e.ROOT


def read(p):return json.loads(pathlib.Path(p).read_text())
def legal_matrix(pa,abbr):
    d=len(pa);legal=np.ones((d,d),dtype=np.bool_)
    for i,a in enumerate(pa[1:],1):
        if len(a)==1:
            for j,b in enumerate(pa[1:],1):legal[i,j]=(a+b[0]) not in abbr
    return legal


def assignment(edges,n,d,legal,preferred,weights,time_limit=5.,node_limit=500):
    """One-hot injective assignment of n observed units to d-1 target units.
    n excludes the sentinel and unused source slots. Rounded incumbents are
    accepted only after exact discrete validation, regardless of solver status.
    """
    m=d-1;N=n*m;cost=np.repeat(np.asarray(weights,dtype=float),m)
    for i in range(n):cost[i*m+preferred[i+1]-1]=0.
    rows=[];cols=[];vals=[];lower=[];upper=[];upper_bound=np.ones(N)
    def add(indices,lo,hi):
        row=len(lower);rows.extend([row]*len(indices));cols.extend(indices);vals.extend([1.]*len(indices));lower.append(lo);upper.append(hi)
    for i in range(n):add([i*m+j for j in range(m)],1,1)
    for j in range(m):add([i*m+j for i in range(n)],0,1)
    bad=[(i,j) for i in range(1,d) for j in range(1,d) if not legal[i,j]]
    for a,b in edges:
        if a==0 or b==0:continue
        for i,j in bad:
            if a==b:
                if i==j:upper_bound[(a-1)*m+i-1]=0
            elif i!=j:add([(a-1)*m+i-1,(b-1)*m+j-1],-np.inf,1)
    A=csc_matrix((vals,(rows,cols)),shape=(len(lower),N))
    result=milp(cost,integrality=np.ones(N),bounds=Bounds(np.zeros(N),upper_bound),
                constraints=LinearConstraint(A,np.asarray(lower),np.asarray(upper)),
                options={'time_limit':time_limit,'node_limit':node_limit,'mip_rel_gap':0.0})
    info={'status':int(result.status),'message':str(result.message),'variables':N,'constraints':len(lower),
          'nodes':int(result.mip_node_count) if getattr(result,'mip_node_count',None) is not None else None,
          'objective':float(result.fun) if getattr(result,'fun',None) is not None else None,
          'mip_gap':float(result.mip_gap) if getattr(result,'mip_gap',None) is not None and math.isfinite(result.mip_gap) else None,
          'valid_incumbent':False,'time_limit':time_limit,'node_limit':node_limit}
    if result.x is None:return None,info
    x=np.asarray(result.x).reshape(n,m)
    if np.max(abs(x-np.rint(x)))>1e-5:return None,info
    x=np.rint(x).astype(int)
    if not np.all(x.sum(1)==1) or np.any(x.sum(0)>1):return None,info
    key=[0]+(np.argmax(x,1)+1).tolist();key+=sorted(set(range(1,d))-set(key[1:]))
    key=np.asarray(key,np.int64)
    if len(key)!=d or len(set(key))!=d or any(not legal[key[a],key[b]] for a,b in edges):return None,info
    info['valid_incumbent']=True
    return key,info


@njit(cache=True)
def constrained_search(gs,ws,lp,d,initial,flat,off,edges,legal,seed=26092604,restarts=6,steps=18000):
    np.random.seed(seed);best=initial.copy();bestscore=e.old.score_key(gs,ws,best,lp,d)
    for restart in range(restarts):
        key=best.copy();score=bestscore
        for step in range(steps):
            a=np.random.randint(1,d);b=np.random.randint(1,d)
            if a==b:continue
            va=key[a];vb=key[b];okay=True
            for k in range(len(edges)):
                x,y=edges[k]
                if x!=a and x!=b and y!=a and y!=b:continue
                xx=vb if x==a else va if x==b else key[x]
                yy=vb if y==a else va if y==b else key[y]
                if not legal[xx,yy]:okay=False;break
            if not okay:continue
            lo,hi=off[a,b];delta=0.
            for z in range(lo,hi):
                g=flat[z];old=0;new=0
                for t in range(4):
                    x=gs[g,t];v=key[x];nv=vb if x==a else va if x==b else v
                    old=old*d+v;new=new*d+nv
                delta+=ws[g]*(lp[new]-lp[old])
            temp=40.*(.005**(step/steps))
            if delta>=0 or np.random.random()<math.exp(max(-700.,delta/temp)):
                key[a]=vb;key[b]=va;score+=delta
                if score>bestscore:bestscore=score;best=key.copy()
    return best,bestscore


def repair_fit(primary,training):
    lp,pa,abbr,_=e.model(primary['language'],8);ca=primary['cipher_alphabet'];d=len(pa)
    allowed={u for u in ca[1:] if not u.startswith('<unused:')};n=len(allowed)
    rr,_,_=e.prepare(training,primary['scheme'],allowed);idx={c:i for i,c in enumerate(ca)}
    edges=np.asarray(sorted({(idx[a],idx[b]) for r in rr for a,b in zip(r,r[1:])}),np.int64)
    legal=legal_matrix(pa,abbr);preferred=np.asarray(primary['key'],np.int64)
    freq=collections.Counter(u for r in rr for u in r);weights=[freq[ca[i]] for i in range(1,n+1)]
    if all(legal[preferred[a],preferred[b]] for a,b in edges):
        initial=preferred;info={'status':0,'message':'Primary key already satisfies every training adjacency; no MILP required.','valid_incumbent':True,'objective':0.}
    else:initial,info=assignment(edges,n,d,legal,preferred,weights)
    if initial is None:return {'solver':info,'fitted':False,'interpretation':'No valid incumbent within the bound, unless status 2 explicitly reports infeasibility. Not a proof against all encodings.'}
    gs,ws=e.grams(rr,ca);flat,off=e.old.impact(gs,d)
    key,score=constrained_search(gs,ws,lp,d,initial,flat,off,edges,legal)
    assert abs(score-e.old.score_key(gs,ws,key,lp,d))<.01
    assert all(legal[key[a],key[b]] for a,b in edges)
    obj={k:primary[k] for k in ['language','abbreviations','abbreviation_inventory','scheme','cipher_alphabet','plaintext_units']}
    obj.update(key=key.tolist(),solver=info,fitted=True,strict_restarts=6,strict_steps=18000,seed=26092604,
               initial_feasible_key=initial.tolist(),train_logscore=float(score),candidate_is_translation=False)
    obj['train']=e.evaluate(training,obj,False)
    assert obj['train']['canonical_run_fraction']==1.0
    return obj


def fit_one(name):
    out=R/'results'/name.replace('fit__','strict_fit__',1)
    if out.exists():return out.name,'exists'
    primary=read(R/'results'/name);t=time.time()
    obj=repair_fit(primary,runner.records(primary['group'],primary['sample_kind'],'train'))
    obj.update(primary_fit_file=name,primary_fit_sha256=e.digest(R/'results'/name),
               group=primary['group'],sample_kind=primary['sample_kind'],language=primary['language'],scheme=primary['scheme'],abbreviations=8,seconds=time.time()-t)
    # No development or confirmation sample is supplied to this routine.
    e.save(out,obj)
    return out.name,{'fitted':obj['fitted'],'status':obj['solver']['status'],'seconds':round(obj['seconds'],1)}


def evaluate_one(name):
    from diagnostics import WordOrder
    out=R/'results'/name.replace('strict_fit__','strict_evaluation__',1)
    if out.exists():return out.name,'exists'
    frozen=read(R/'STRICT_FROZEN_KEYS.json');p=R/'results'/name;assert e.digest(p)==frozen['keys'][name]
    fit=read(p)
    if not fit['fitted']:return name,'no fitted key'
    b=read(R/'results'/('boundary__'+fit['language']+'.json'));seg=e.Segmenter(fit['language'],b['selected_unknown_mass'])
    dev=e.evaluate(runner.records(fit['group'],fit['sample_kind'],'development'),fit,False)
    conf=e.evaluate(runner.records(fit['group'],fit['sample_kind'],'confirmation'),fit,True)
    for row in conf['outputs']:row['inferred_words']=seg.segment(row['letters'])
    diag=WordOrder(fit['language']).summary([r['inferred_words'] for r in conf['outputs']])
    obj={'fit_file':name,'fit_sha256':e.digest(p),'group':fit['group'],'language':fit['language'],
         'scheme':fit['scheme'],'sample_kind':fit['sample_kind'],'development':dev,'confirmation':conf,'word_order':diag,
         'all_three_samples_roundtrip':dev['canonical_run_fraction']==conf['canonical_run_fraction']==fit['train']['canonical_run_fraction']==1.0,
         'confirmation_not_previously_unseen':True,'candidate_is_translation':False}
    e.save(out,obj);return out.name,{'all_three_roundtrip':obj['all_three_samples_roundtrip']}


def calibration_checks():
    out=[]
    for p in sorted((R/'results').glob('calibration__*__8__*.json')):
        obj=read(p);primary=obj['fit'];lang=primary['language'];abbr=primary['abbreviation_inventory']
        # Construct training ciphertext from the saved true forward encoding.
        training=[]
        for j,run in enumerate(runner.pack(lang,'dev',2500)):
            text=''.join(obj['plaintext_to_cipher'][u] for u in e.chunk(''.join(run),abbr))
            training.append({'id':f'control.{j}','words':[text]})
        strict=repair_fit(primary,training);assert strict['fitted']
        mp=dict(zip(strict['cipher_alphabet'],[strict['plaintext_units'][i] for i in strict['key']]))
        good=total=0
        for row in obj['test']['outputs']:
            for u,c in zip(row['true_units'],row['cipher_units']):good+=len(u)*(mp.get(c)==u);total+=len(u)
        out.append({'source':p.name,'weighted_correct':good,'weighted_total':total,'accuracy':good/total,'key':strict['key'],
                    'initial_feasible_key':strict['initial_feasible_key'],'note':'Full pipeline check from the already calibrated key; not a standalone blind test of the MILP repair.'})
    e.save(R/'results'/'STRICT_CALIBRATION.json',out)
    return out


def tiny_checks():
    pa=['<RUN>','a','b','ab'];legal=legal_matrix(pa,['ab'])
    edges=np.asarray([(1,2),(2,1)],np.int64);preferred=np.asarray([0,1,2,3]);weights=[2,1]
    key,info=assignment(edges,2,4,legal,preferred,weights,time_limit=5,node_limit=500)
    possible=[]
    for perm in itertools.permutations(range(1,4),2):
        k=[0]+list(perm)
        if all(legal[k[a],k[b]] for a,b in edges):possible.append(sum(weights[i]*(k[i+1]!=preferred[i+1]) for i in range(2)))
    assert key is not None and info['status']==0 and info['objective']==min(possible)
    impossible=np.asarray([(a,b) for a in range(1,4) for b in range(1,4)],np.int64)
    key,bad=assignment(impossible,3,4,legal,preferred,[1,1,1])
    assert key is None and bad['status']==2
    e.save(R/'results'/'STRICT_TINY_CHECKS.json',{'feasible_matches_exhaustive_minimum':True,'infeasibility_matches_exhaustive_enumeration':True,'feasible_solver':info,'infeasible_solver':bad})


def main():
    ap=argparse.ArgumentParser();ap.add_argument('stage',choices=['freeze','calibrate','fit','freeze-keys','evaluate']);ap.add_argument('--workers',type=int,default=2);a=ap.parse_args()
    if a.stage=='freeze':
        p=R/'STRICT_PROTOCOL.json'
        if p.exists():raise FileExistsError(p)
        obj={'frozen_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
             'stage':'Post-primary exploratory extension after all primary confirmation outputs were available. Not independent confirmation.',
             'trigger':'All sixteen primary abbreviation keys violate canonical forward encoding in at least one retained training run; some pass only confirmation.',
             'selection':'All 48 primary eight-abbreviation configurations, including both matched null kinds; no selection by language or confirmation score.',
             'method':'Train-only binary injective assignment with every observed adjacency constrained to legal greedy abbreviation pairs. Minimize frequency-weighted changes from the primary key with a five-second, 500-node bound. Accept only exactly verified discrete incumbents. Then six 18000-proposal feasible-only swap anneals maximize the same reference model.',
             'key_freeze':'Save all strict fitted keys before rescoring development and the previously used confirmation samples. No changes to old files.',
             'limits':'No feasible incumbent by the bound is not infeasibility. A feasible assignment is not plaintext. A local constrained swap search is not a global optimum. Relative word-order diagnostics remain exploratory and unadjusted.',
             'code_sha256':e.digest(pathlib.Path(__file__))}
        e.save(p,obj);return print('Strict extension frozen')
    if a.stage=='calibrate':tiny_checks();return print('strict calibration',[(x['source'],x['accuracy']) for x in calibration_checks()])
    if a.stage=='freeze-keys':
        p=R/'STRICT_FROZEN_KEYS.json'
        if p.exists():raise FileExistsError(p)
        keys={p.name:e.digest(p) for p in (R/'results').glob('strict_fit__*.json')};assert len(keys)==48
        e.save(p,{'frozen_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'keys':keys,'protocol_sha256':e.digest(R/'STRICT_PROTOCOL.json'),'code_sha256':e.digest(pathlib.Path(__file__))});return print('Strict keys frozen',len(keys))
    if a.stage=='fit':
        cal=read(R/'results'/'STRICT_CALIBRATION.json');assert len(cal)==8 and min(x['accuracy'] for x in cal)>=.99
        names=[p.name for p in sorted((R/'results').glob('fit__*__8__*.json'))];fn=fit_one
    else:names=[p.name for p in sorted((R/'results').glob('strict_fit__*.json'))];fn=evaluate_one
    with concurrent.futures.ProcessPoolExecutor(max_workers=a.workers) as pool:
        futures=[pool.submit(fn,n) for n in names]
        for f in concurrent.futures.as_completed(futures):print(*f.result(),flush=True)
if __name__=='__main__':main()
