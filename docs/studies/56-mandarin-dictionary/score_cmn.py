# python3 score_cmn.py test.tsv <phrase column> results.txt: exact after one Space, both sides folded to Simplified
# characters with OpenCC's TSCharacters (first mapping), so the test's mixed scripts compare with Rime's Simplified.
import os, sys
TS = os.path.expanduser('~/build/tk-rime/src/librime/deps/opencc/data/dictionary/TSCharacters.txt')
fold = {}
for line in open(TS, encoding='utf-8'):
    a, _, b = line.rstrip('\n').partition('\t')
    if len(a) == 1 and b:
        fold[a] = b.split(' ')[0]
f = lambda t: ''.join(fold.get(c, c) for c in t)
col = int(sys.argv[2]) - 1
tests = [l.rstrip('\n').split('\t')[col] for l in open(sys.argv[1], encoding='utf-8')]
got = [l.rstrip('\n').partition('|') for l in open(sys.argv[3], encoding='utf-8')]
assert len(tests) == len(got), (len(tests), len(got))
buckets = {}
exact = chars = total = left = 0
for want, (typed, _, rest) in zip(tests, got):
    want, typed = f(want), f(typed)
    left += bool(rest)
    ok = typed == want
    exact += ok
    total += len(want)
    chars += sum(a == b for a, b in zip(typed, want)) if len(typed) == len(want) else 0
    b = '2-3' if len(want) <= 3 else '4-6' if len(want) <= 6 else '7-10' if len(want) <= 10 else '11+'
    n, k = buckets.get(b, (0, 0)); buckets[b] = (n + 1, k + ok)
n = len(tests)
print(f'exact {100*exact/n:.1f}%  chars {100*chars/total:.1f}%  left-over {left}  ' +
      '  '.join(f'{b}: {100*k/m:.1f}% ({m})' for b, (m, k) in sorted(buckets.items(), key=lambda x: int(x[0].split("-")[0].rstrip("+")))))
