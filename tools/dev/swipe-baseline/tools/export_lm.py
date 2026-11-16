#!/usr/bin/env python3
"""Exports a small language model to ONNX and shrinks it to 8-bit weights, for timing and use on the phone.
The exported graph takes token ids and gives the chances of the next token after the last one.
Usage: export_lm.py model-name out-dir"""
import os
import sys

import torch
from onnxruntime.quantization import QuantType, quantize_dynamic
from transformers import AutoModelForCausalLM, AutoTokenizer

# The exporter prints symbols the Windows console cannot show, and -I ignores PYTHONIOENCODING.
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
sys.stderr.reconfigure(encoding='utf-8', errors='replace')
name, out = sys.argv[1:3]
os.makedirs(out, exist_ok=True)
tok = AutoTokenizer.from_pretrained(name)
# Plain attention exports cleanly; the fused kinds do not.
model = AutoModelForCausalLM.from_pretrained(name, dtype=torch.float32, attn_implementation='eager').eval()
model.config.use_cache = False


class NextToken(torch.nn.Module):
    def __init__(self, lm):
        super().__init__()
        self.lm = lm

    def forward(self, input_ids):
        # Only the last position is needed, so the 50,000-word output layer runs once instead of once per token.
        hidden = self.lm.base_model(input_ids=input_ids, use_cache=False).last_hidden_state[:, -1:, :]
        logits = self.lm.get_output_embeddings()(hidden)
        return torch.log_softmax(logits[:, -1, :], -1)


wrapper = NextToken(model).eval()
sample = torch.tensor([tok('see you at the')['input_ids']], dtype=torch.long)
fp32 = os.path.join(out, 'model.onnx')
tokens = torch.export.Dim('tokens', min=1, max=256)
program = torch.onnx.export(wrapper, (sample,), input_names=['input_ids'], output_names=['next_logp'],
                            dynamic_shapes={'input_ids': {1: tokens}}, opset_version=18, dynamo=True, external_data=False)
program.save(fp32)
# Per-channel 8-bit: plain 8-bit cost about a point of first choice in the full-stack test, per-channel cost nothing.
int8 = os.path.join(out, 'model-int8pc.onnx')
quantize_dynamic(fp32, int8, weight_type=QuantType.QInt8, per_channel=True)
tok.save_pretrained(out)
# The phone's tokenizer (ByteBpe.java) reads the vocabulary one token per line in id order, and the merges in rank order.
import json
spec = json.loads(tok.backend_tokenizer.to_str())
vocab = spec['model']['vocab']
by_id = sorted(vocab.items(), key=lambda kv: kv[1])
assert [i for _, i in by_id] == list(range(len(by_id))), 'token ids must be dense'
with open(os.path.join(out, 'lm-vocab.txt'), 'w', encoding='utf-8', newline='\n') as f:
    for token, _ in by_id:
        assert '\n' not in token and '\r' not in token
        f.write(token + '\n')
with open(os.path.join(out, 'lm-merges.txt'), 'w', encoding='utf-8', newline='\n') as f:
    for m in spec['model']['merges']:
        f.write((m if isinstance(m, str) else ' '.join(m)) + '\n')
digits = 'individual_digits' in json.dumps(spec.get('pre_tokenizer'))
bos = tok.bos_token_id if tok.bos_token_id is not None else tok.eos_token_id
with open(os.path.join(out, 'lm-info.txt'), 'w', encoding='utf-8', newline='\n') as f:
    f.write(f'name={name}\nsplit_digits={str(digits).lower()}\nstart_token={bos}\n')
# Reference encodings, so the phone's tokenizer can be checked against this one.
samples = ['see you at the', " I'm going to the shop's door, 12 times!", ' Hello\n\nworld  ok', ' naïve café 2026', ' p', ' P', ' you', ' You']
with open(os.path.join(out, 'lm-check.txt'), 'w', encoding='utf-8', newline='\n') as f:
    for s in samples:
        f.write(json.dumps({'text': s, 'ids': tok(s, add_special_tokens=False)['input_ids']}) + '\n')
for path in (fp32, int8):
    print(os.path.basename(path), round(os.path.getsize(path) / 1e6, 1), 'MB')
