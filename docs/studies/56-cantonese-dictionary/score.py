# python3 score.py test.tsv results.txt: exact phrases after one Space, characters right, by length.
import sys
tests = [l.rstrip('\n').split('\t') for l in open(sys.argv[1], encoding='utf-8')]
got = [l.rstrip('\n') for l in open(sys.argv[2], encoding='utf-8')]
assert len(tests) == len(got), (len(tests), len(got))
buckets = {}
exact = chars = total = left = 0
for (code, want), g in zip(tests, got):
    typed, _, rest = g.partition('|')
    if rest: left += 1
    ok = typed == want
    exact += ok
    total += len(want)
    chars += sum(a == b for a, b in zip(typed, want)) if len(typed) == len(want) else 0
    b = '2-3' if len(want) <= 3 else '4-6' if len(want) <= 6 else '7-10' if len(want) <= 10 else '11+'
    n, k = buckets.get(b, (0, 0)); buckets[b] = (n + 1, k + ok)
n = len(tests)
print(f'exact {100*exact/n:.1f}%  chars {100*chars/total:.1f}%  left-over {left}  ' +
      '  '.join(f'{b}: {100*k/m:.1f}% ({m})' for b, (m, k) in sorted(buckets.items(), key=lambda x: int(x[0].split("-")[0].rstrip("+")))))
