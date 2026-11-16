"""Fine-tunes DistilGPT-2 on everyday conversation (SODA, CC-BY-4.0), so the phone's next-word model knows how people write
messages. Public data only: nobody's own text is used or needed here. Each utterance is one example that starts fresh,
like a message typed into an empty field. Saves checkpoints to judge at home on held-back typing.
Usage: python lm_finetune.py out-dir [max-steps]"""
import math
import os
import random
import sys
import time

import torch
from datasets import load_dataset
from transformers import AutoModelForCausalLM, AutoTokenizer

OUT = sys.argv[1]
STEPS = int(sys.argv[2]) if len(sys.argv) > 2 else 20000
SAVE_AT = {1000, 3000, 8000, 20000, STEPS}
BATCH, LENGTH, LR = 64, 64, 5e-5
os.makedirs(OUT, exist_ok=True)
torch.manual_seed(5)
random.seed(5)


def log(*parts):
    line = time.strftime('%H:%M:%S ') + ' '.join(str(p) for p in parts)
    print(line, flush=True)
    with open(os.path.join(OUT, 'train.log'), 'a', encoding='utf-8') as f:
        f.write(line + '\n')


tok = AutoTokenizer.from_pretrained('distilgpt2')
model = AutoModelForCausalLM.from_pretrained('distilgpt2').cuda()
data = load_dataset('allenai/soda', split='train')
log('dialogues', len(data))
utterances = [u.strip() for d in data['dialogue'] for u in d if 3 <= len(u.strip()) <= 400]
random.shuffle(utterances)
held = utterances[:5000]
train = utterances[5000:]
log('utterances', len(train), 'held out', len(held))
EOS = tok.eos_token_id


def batch_of(texts):
    rows = [[EOS] + tok(t, add_special_tokens=False)['input_ids'][:LENGTH - 1] for t in texts]
    width = max(len(r) for r in rows)
    ids = torch.full((len(rows), width), EOS, dtype=torch.long)
    mask = torch.zeros((len(rows), width), dtype=torch.long)
    for i, r in enumerate(rows):
        ids[i, :len(r)] = torch.tensor(r)
        mask[i, :len(r)] = 1
    labels = ids.masked_fill(mask == 0, -100)
    return ids.cuda(), mask.cuda(), labels.cuda()


def held_loss():
    model.eval()
    total, n = 0.0, 0
    with torch.no_grad():
        for i in range(0, len(held), 256):
            ids, mask, labels = batch_of(held[i:i + 256])
            with torch.autocast('cuda', dtype=torch.float16):
                total += model(input_ids=ids, attention_mask=mask, labels=labels).loss.item()
            n += 1
    model.train()
    return total / n


opt = torch.optim.AdamW(model.parameters(), lr=LR, weight_decay=.01)
sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=LR, total_steps=STEPS + 2, pct_start=.03)
scaler = torch.amp.GradScaler()
log('held-out loss before', round(held_loss(), 4))
model.train()
t0 = time.time()
for step in range(1, STEPS + 1):
    texts = [train[random.randrange(len(train))] for _ in range(BATCH)]
    ids, mask, labels = batch_of(texts)
    with torch.autocast('cuda', dtype=torch.float16):
        loss = model(input_ids=ids, attention_mask=mask, labels=labels).loss
    opt.zero_grad(set_to_none=True)
    scaler.scale(loss).backward()
    scaler.unscale_(opt)
    torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
    scaler.step(opt)
    scaler.update()
    sched.step()
    if step % 500 == 0:
        log(f'step {step} loss {loss.item():.4f} ({(time.time() - t0) / 60:.1f} min)')
    if step in SAVE_AT:
        path = os.path.join(OUT, f'step-{step}')
        model.save_pretrained(path)
        tok.save_pretrained(path)
        log(f'saved {path}, held-out loss {held_loss():.4f}')
log('all done')
