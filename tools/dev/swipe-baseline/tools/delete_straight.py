"""Replays today's delete flick rule over every raw log and tries tighter shapes. Counts only, no text."""
import json, math, sys, glob, os, collections
RIGHT_EDGE, EDGE_START_TOP, EDGE_START_BOTTOM, EDGE_END_TOP, EDGE_END_BOTTOM, EDGE_WANDER = .875, .1, .62, .175, .625, 1.15
INNER_EDGE, MIDDLE_ROW_TOP, INNER_START_BOTTOM, INNER_END_BOTTOM, INNER_TILT, INNER_WANDER = .76, .25, .56, .6, .1, 1.1
MIN_TRAVEL, TURN_BACK = .1, .05
def path_keys(p): return sum(math.hypot((b[0]-a[0])*10,(b[1]-a[1])*4) for a,b in zip(p,p[1:]))
def feats(p):
    f,l=p[0],p[-1]
    chord=math.hypot((l[0]-f[0])*10,(l[1]-f[1])*4)
    return dict(travel=(f[0]-l[0])*10, path=path_keys(p), wander=path_keys(p)/max(chord,1e-6), tilt=abs(l[1]-f[1]), ms=l[2]-f[2], y0=f[1], x0=f[0])
def today(p):
    f,l=p[0],p[-1]
    if f[0]-l[0]<MIN_TRAVEL or any(q[0]<l[0]-TURN_BACK for q in p): return False
    w=path_keys(p)/max(math.hypot((l[0]-f[0])*10,(l[1]-f[1])*4),1e-6)
    if f[0]>=RIGHT_EDGE: return EDGE_START_TOP<=f[1]<EDGE_START_BOTTOM and EDGE_END_TOP<=l[1]<EDGE_END_BOTTOM and w<=EDGE_WANDER
    return f[0]>=INNER_EDGE and MIDDLE_ROW_TOP<=f[1]<INNER_START_BOTTOM and MIDDLE_ROW_TOP<=l[1]<INNER_END_BOTTOM and abs(l[1]-f[1])<=INNER_TILT and w<=INNER_WANDER
seen=set(); rows=[]
recent_cut=None
files=sys.argv[1:]
for path in files:
    off=None; contacts={}; cur=None; ev=[]
    for line in open(path,encoding='utf-8'):
        try: d=json.loads(line)
        except ValueError: continue
        k=d.get('k'); t=d.get('t')
        if k=='header': off=d['wall']-d['t']; continue
        if k=='m':
            pts=[s for s in d.get('s',[]) if s and s[0] is not None]
            if d['a']==0: cur={'down':t,'pts':list(pts)}
            elif cur is not None:
                cur['pts']+=pts
                if d['a'] in (1,3): contacts[cur['down']]=cur; cur=None
        if k=='out' and d.get('type')=='swipe': ev.append((off,d))
    for off,d in ev:
            key=(round(off/60000) if off else None, d['start'])
            c=contacts.get(d['start'])
            if key in seen or c is None or len(c['pts'])<2: continue
            seen.add(key)
            p=[q for q in c['pts'] if d['start']<=q[2]<=d['end']] or c['pts']
            if len(p)<2: continue
            rows.append(dict(p=p, wall=(d['start']+off) if off else 0, logged=bool(d.get('deletes'))))
rows.sort(key=lambda r: r['wall'])
for a, b in zip(rows, rows[1:]):
    # A retry: the next swipe comes within 4 s from about the same start, and is long too. A real delete is followed by typing.
    a['retry'] = b['wall'] - a['p'][-1][2] + a['p'][0][2] - a['wall'] < 4000 and abs(b['p'][0][0]-a['p'][0][0]) < .08 and abs(b['p'][0][1]-a['p'][0][1]) < .1 and (b['p'][0][0]-b['p'][-1][0])*10 > 3
fired=[r for r in rows if today(r['p'])]
last=max(r['wall'] for r in rows)
for r in fired: r.update(feats(r['p']))
recent=[r for r in fired if r['wall']>last-3600e3]
false_recent=[r for r in recent if r['travel']>5 or r['ms']>400]
print('swipes',len(rows),'today fires',len(fired),'logged deletes',sum(r['logged'] for r in rows))
def pct(vals,qs=(50,90,99,99.5,99.9,100)):
    v=sorted(vals); return {q: round(v[min(len(v)-1,int(len(v)*q/100))],3) for q in qs}
older=[r for r in fired if r['wall']<=last-3600e3]
for f in ('travel','path','ms','tilt','wander'):
    print(f,'all older fires',pct([r[f] for r in older]))
print('recent false', len(false_recent), [(round(r['travel'],1),r['ms'],round(r['tilt'],2),round(r['wander'],3)) for r in false_recent])
def rule(r, travel=None, ms=None, tilt=None, wander=None, path=None):
    return ((travel is None or r['travel']<=travel) and (ms is None or r['ms']<=ms) and (tilt is None or r['tilt']<=tilt)
            and (wander is None or r['wander']<=wander) and (path is None or r['path']<=path))
cands=[dict(travel=5.5, ms=600, wander=1.1), dict(travel=5.5, ms=600, wander=1.08), dict(travel=5.5, ms=600, tilt=.25), dict(travel=5.5, ms=600), dict(travel=6, ms=600), dict(travel=5.5, ms=800), dict(path=6, ms=700), dict(travel=5), dict(travel=5.5), dict(travel=6), dict(path=5.5), dict(ms=400), dict(ms=350), dict(travel=6, ms=400),
       dict(travel=5.5, ms=400), dict(travel=5, ms=400), dict(travel=5.5, ms=400, tilt=.12), dict(travel=5.5, ms=400, wander=1.05),
       dict(path=5.5, ms=400, tilt=.12), dict(path=5.5, ms=450, tilt=.15), dict(path=6, ms=500, tilt=.15)]
for c in cands:
    lost=[r for r in older if not rule(r,**c)]
    fixed=[r for r in false_recent if not rule(r,**c)]
    lostrec=[r for r in recent if r not in false_recent and not rule(r,**c)]
    print(c,'older fires lost',len(lost),'of',len(older),'(retried',sum(1 for r in lost if r.get('retry')),')','| recent false fixed',len(fixed),'of',len(false_recent),'| recent real lost',len(lostrec))
    if len(lost)<=40: print('   lost shapes', sorted((round(r['travel'],1),r['ms'],round(r['tilt'],2)) for r in lost)[:40])

# The rule as built: today's, and a path of at most 5.5 keys, arriving within 600 ms, with the right edge's wander at most 1.1.
def arrival(p):
    l = p[-1]
    a = next(q for q in p if math.hypot((l[0]-q[0])*10, (l[1]-q[1])*4) <= .25)
    return a[2] - p[0][2]
def built(r):
    p = r['p']
    if not today(p) or path_keys(p) > 5.5 or arrival(p) > 600: return False
    return not (p[0][0] >= RIGHT_EDGE and r['wander'] > 1.1)
lost = [r for r in older if not built(r)]
print('BUILT: older fires lost', len(lost), 'of', len(older), '(%.1f%%)' % (100*len(lost)/len(older)), 'retried', sum(1 for r in lost if r.get('retry')),
      '| recent false fixed', sum(1 for r in false_recent if not built(r)), 'of', len(false_recent),
      '| recent real lost', sum(1 for r in recent if r not in false_recent and not built(r)), 'of', len(recent) - len(false_recent))
print('  lost by size: over 5.5 keys', sum(1 for r in lost if r['path'] > 5.5), 'slow only', sum(1 for r in lost if r['path'] <= 5.5 and arrival(r['p']) > 600),
      'wander only', sum(1 for r in lost if r['path'] <= 5.5 and arrival(r['p']) <= 600))
print('  lost under 4 keys', sum(1 for r in lost if r['path'] < 4))
print('missed false:', [(round(r['path'],1), r['ms'], arrival(r['p'])) for r in false_recent if built(r)])
short = [r for r in older if path_keys(r['p']) <= 5.5]
print('arrival pct of older fires under 5.5 keys', pct([arrival(r['p']) for r in short], (50, 90, 99, 99.5, 99.8, 99.9, 100)))
for cap in (350, 400, 450, 500, 550):
    print(' cap', cap, 'loses', sum(1 for r in short if arrival(r['p']) > cap), 'fixes missed', sum(1 for r in false_recent if built(r) and arrival(r['p']) > cap))
