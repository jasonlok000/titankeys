# The test's codes with 佢 read keoi, as the dictionary has it, instead of Tatoeba's heoi.
import re, sys
SYL = re.compile(r'(?:ng|gw|kw|[bpmfdtnlgkhzcsjw])?(?:aa|oe|eo|yu|[aeiou])(?:[iuy]|ng|[mnptk])?')
for line in open(sys.argv[1], encoding='utf-8'):
    code, text = line.rstrip('\n').split('\t')
    syls = SYL.findall(code)
    if ''.join(syls) == code and len(syls) == len(text):
        syls = ['keoi' if ch == '佢' and s == 'heoi' else s for s, ch in zip(syls, text)]
        code = ''.join(syls)
    print(code + '\t' + text)
