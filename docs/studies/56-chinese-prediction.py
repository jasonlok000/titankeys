#!/usr/bin/env python3
"""
#56's study: Chinese next-word suggestions from librime-predict's table, or from a small Chinese language model.

After each committed word the bar would offer what comes next (after 你: 好, 哋, 的…). This measures how often the next
character is among the first 1 and 3 offered, on held-out Tatoeba sentences: all Cantonese (yue), and the Mandarin (cmn) ones in
Traditional characters. Words are cut the way Rime commits them, by the longest word in its word lists.

- Table: librime-predict's data built as its own make_predict_data builds it, from the word lists TitanKeys already ships
  (rime-essay for Pinyin, rime-cantonese's essay for Cantonese: a word's start offers its rest), alone, and with word pairs
  counted on the other 80% of the Tatoeba sentences. Looked up by the last committed word, as the plugin does.
- Models: each exported to ONNX as the English DistilGPT-2 is (tools/dev/swipe-baseline/tools/export_lm.py) and made 8-bit
  per channel, except Qwen, which that turns to noise: it gets 4-bit weights only. Each reads the sentence so far, at most 32
  tokens, and its top tokens are read as their first character.

Public text only: Tatoeba (CC BY 2.0 FR). Everything is downloaded outside the repo, into ~/data/zh (see 56-chinese-prediction.md).

Usage: python3 56-chinese-prediction.py export   # ONNX 8-bit models, their sizes
       python3 56-chinese-prediction.py eval     # the scores and the speeds
"""
import hashlib
import json
import os
import shutil
import sys
import time
from collections import Counter, defaultdict

import numpy as np

DATA = os.path.expanduser("~/data/zh")
RIME = os.path.expanduser("~/build/tk-rime/src")
OPENCC = RIME + "/librime/deps/opencc/data/dictionary"
OUT = DATA + "/onnx"
CAP = 32
POSITIONS = 3000  # per test set and model, the same sample for every method
MODELS = {
    "ckip-tiny": ("ckiplab/gpt2-tiny-chinese", "bert"),
    "uer-distil": ("uer/gpt2-distil-chinese-cluecorpussmall", "bert"),
    "ckip-base": ("ckiplab/gpt2-base-chinese", "bert"),
    "qwen-0.5b": ("Qwen/Qwen2.5-0.5B", "bpe"),
}


def cjk(c):
    return "㐀" <= c <= "鿿" or "\U00020000" <= c <= "\U0003134f"


# --- the text --------------------------------------------------------------------------------------------------------------

def opencc_keys(name):
    keys = set()
    for line in open(f"{OPENCC}/{name}", encoding="utf-8"):
        key, _, values = line.rstrip("\n").partition("\t")
        if len(key) == 1 and values.split(" ")[0] != key:
            keys.add(key)
    return keys


def traditional_test():
    """Tatoeba's Cantonese, and its Mandarin written in Traditional characters, each split 80/20 by sentence id."""
    trad_only = opencc_keys("TSCharacters.txt") - opencc_keys("STCharacters.txt")
    simp_only = opencc_keys("STCharacters.txt") - opencc_keys("TSCharacters.txt")
    sets = {}
    for lang in ("yue", "cmn"):
        seen, train, test = set(), [], []
        for line in open(f"{DATA}/text/{lang}_sentences.tsv", encoding="utf-8"):
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 3 or parts[2] in seen:
                continue
            text = parts[2]
            seen.add(text)
            if lang == "cmn" and (not any(c in trad_only for c in text) or any(c in simp_only for c in text)):
                continue
            (test if int(hashlib.md5(parts[0].encode()).hexdigest(), 16) % 5 == 0 else train).append(text)
        sets[lang] = (train, test)
    return sets


def word_list(name):
    words = {}
    for line in open(name, encoding="utf-8"):
        parts = line.rstrip("\n").split("\t")
        if len(parts) >= 2 and parts[0] and not parts[0].startswith("#"):
            try:
                words[parts[0]] = max(words.get(parts[0], 0), int(parts[1]))
            except ValueError:
                pass
    return words


def cut(text, words, longest=6):
    """Longest match from the left, as a stand-in for what Rime commits. Anything not Chinese is a word of its own."""
    out, at = [], 0
    while at < len(text):
        if not cjk(text[at]):
            out.append(text[at])
            at += 1
            continue
        for n in range(min(longest, len(text) - at), 0, -1):
            if n == 1 or text[at:at + n] in words:
                out.append(text[at:at + n])
                at += n
                break
    return out


def positions(test, words, seed=0):
    """(text so far, last word, next word) after each Chinese word that a Chinese word follows. A fixed sample."""
    found = []
    for text in test:
        pieces = cut(text, words)
        done = ""
        for i in range(len(pieces) - 1):
            done += pieces[i]
            if cjk(pieces[i][0]) and cjk(pieces[i + 1][0]):
                found.append((done, pieces[i], pieces[i + 1]))
    rng = np.random.default_rng(seed)
    if len(found) > POSITIONS:
        found = [found[i] for i in sorted(rng.choice(len(found), POSITIONS, replace=False))]
    return found


# --- librime-predict's table --------------------------------------------------------------------------------------------------

def table_from_words(words):
    """make_predict_data's rule: each word "ABC" adds A -> BC and AB -> C, keeping the highest weight."""
    table = defaultdict(dict)
    for word, weight in words.items():
        for i in range(1, len(word)):
            key, rest = word[:i], word[i:]
            if table[key].get(rest, -1) < weight:
                table[key][rest] = weight
    return {key: [r for r, _ in sorted(rests.items(), key=lambda x: -x[1])] for key, rests in table.items()}


def table_from_pairs(train, words):
    pairs = defaultdict(Counter)
    for text in train:
        pieces = cut(text, words)
        for a, b in zip(pieces, pieces[1:]):
            if cjk(a[0]) and cjk(b[0]):
                pairs[a][b] += 1
    return {key: [b for b, _ in counts.most_common()] for key, counts in pairs.items()}


def first_chars(candidates, k=3):
    out = []
    for c in candidates:
        if c and c[0] not in out:
            out.append(c[0])
        if len(out) == k:
            break
    return out


# --- the models ---------------------------------------------------------------------------------------------------------------

def export():
    import torch
    from onnxruntime.quantization import QuantType, quantize_dynamic
    from transformers import AutoModelForCausalLM

    class Last(torch.nn.Module):
        """The model's log chances for the next token only, as the English model's export gives (next_logp)."""

        def __init__(self, model):
            super().__init__()
            self.model = model

        def forward(self, input_ids):
            # As tools/dev/swipe-baseline/tools/export_lm.py: the output layer runs on the last position only.
            hidden = self.model.base_model(input_ids=input_ids, use_cache=False).last_hidden_state[:, -1:, :]
            logits = self.model.get_output_embeddings()(hidden)
            return torch.log_softmax(logits[:, -1, :], -1)

    os.makedirs(OUT, exist_ok=True)
    for name, (repo, _) in MODELS.items():
        target = f"{OUT}/{name}-int8pc.onnx"
        if os.path.exists(target):
            print(f"{name}: {os.path.getsize(target) / 1e6:.0f} MB (already made)")
            continue
        model = AutoModelForCausalLM.from_pretrained(f"{DATA}/models/{repo}", dtype=torch.float32, attn_implementation="eager")
        model.eval()
        model.config.use_cache = False
        params = sum(p.numel() for p in model.parameters())
        # The 32-bit export can be over protobuf's 2 GB, so it goes to a folder of its own with its weights beside it.
        work = f"{OUT}/{name}-work"
        os.makedirs(work, exist_ok=True)
        plain = f"{work}/model.onnx"
        tokens = torch.export.Dim("tokens", min=1, max=256)
        with torch.no_grad():
            # A real sentence's ids as the sample: 你好，我 in BERT's vocabulary, 今天天氣很好，我們 in Qwen's.
            sample = [101, 872, 1962, 8024, 2769] if MODELS[name][1] == "bert" else [100644, 35727, 104200, 99165, 52801, 3837, 97639]
            program = torch.onnx.export(Last(model).eval(), (torch.tensor([sample]),), input_names=["input_ids"],
                                        output_names=["next_logp"], dynamic_shapes={"input_ids": {1: tokens}},
                                        opset_version=18, dynamo=True, external_data=params > 400e6)
        program.save(plain, external_data=params > 400e6)
        if MODELS[name][1] == "bert":
            quantize_dynamic(plain, target, weight_type=QuantType.QInt8, per_channel=True, use_external_data_format=False)
        else:
            # Qwen comes out of the English recipe (8-bit weights and activations) as noise: the same answer for any text. Weights
            # only, 4-bit, keeps its answers; its 151,936-token embedding stays 32-bit, so the file stays large.
            import onnx
            from onnxruntime.quantization.matmul_nbits_quantizer import DefaultWeightOnlyQuantConfig, MatMulNBitsQuantizer
            quantizer = MatMulNBitsQuantizer(onnx.load(plain), algo_config=DefaultWeightOnlyQuantConfig(block_size=32, is_symmetric=True, bits=4))
            quantizer.process()
            quantizer.model.save_model_to_file(target, use_external_data_format=False)
        shutil.rmtree(work)
        print(f"{name}: {params / 1e6:.0f} M parameters, quantized {os.path.getsize(target) / 1e6:.0f} MB")


class Model:
    def __init__(self, name, threads):
        import onnxruntime as ort
        from transformers import AutoTokenizer
        repo, kind = MODELS[name]
        options = ort.SessionOptions()
        options.intra_op_num_threads = threads
        options.inter_op_num_threads = 1
        self.session = ort.InferenceSession(f"{OUT}/{name}-int8pc.onnx", options, providers=["CPUExecutionProvider"])
        self.kind = kind
        if kind == "bert":
            # BERT's Chinese vocabulary gives each character its own id, so the tokenizer is a lookup, as a phone's would be.
            vocab = [line.rstrip("\n") for line in open(f"{DATA}/models/google-bert/bert-base-chinese/vocab.txt", encoding="utf-8")]
            self.char_ids = {t: i for i, t in enumerate(vocab)}
            self.start = [self.char_ids["[CLS]"]]
            self.first = [t[0] if len(t) == 1 and cjk(t) else None for t in vocab]
        else:
            self.tok = AutoTokenizer.from_pretrained(f"{DATA}/models/{repo}")
            self.start = [self.tok.convert_tokens_to_ids("<|endoftext|>")]
            self.first = []
            for i in range(len(self.tok)):
                text = self.tok.decode([i])
                self.first.append(text[0] if text and cjk(text[0]) else None)

    def ids(self, text):
        if self.kind == "bert":
            unknown = self.char_ids["[UNK]"]
            ids = self.start + [self.char_ids.get(c, self.char_ids.get(c.lower(), unknown)) for c in text if not c.isspace()]
            return ids[-CAP:]
        ids = self.start + self.tok.encode(text, add_special_tokens=False)
        return ids[-CAP:]

    def next_chars(self, text, k=3):
        logp = self.session.run(None, {"input_ids": np.array([self.ids(text)], dtype=np.int64)})[0][0]
        if self.kind == "bert":
            out = []
            for i in np.argsort(-logp)[:50]:
                c = self.first[i]
                if c and c not in out:
                    out.append(c)
                if len(out) == k:
                    break
            return out
        # Byte BPE tokens can be whole words: a character's chance is the sum over the tokens it starts.
        chance = defaultdict(float)
        for i in np.argsort(-logp)[:200]:
            c = self.first[i]
            if c:
                chance[c] += float(np.exp(logp[i]))
        return [c for c, _ in sorted(chance.items(), key=lambda x: -x[1])[:k]]


def score(name, cases, predict):
    top1 = top3 = offered = 0
    for done, last, nxt in cases:
        got = predict(done, last)
        if got:
            offered += 1
        top1 += bool(got) and got[0] == nxt[0]
        top3 += nxt[0] in got[:3]
    n = len(cases)
    print(f"  {name:32} top-1 {100 * top1 / n:5.1f}%  top-3 {100 * top3 / n:5.1f}%  offers something {100 * offered / n:5.1f}%")


def evaluate():
    sets = traditional_test()
    lists = {"yue": word_list(f"{RIME}/rime-cantonese/essay-cantonese.txt"), "cmn": word_list(f"{RIME}/rime-essay/essay.txt")}
    models = {name: Model(name, 4) for name in MODELS if os.path.exists(f"{OUT}/{name}-int8pc.onnx")}
    for lang in ("yue", "cmn"):
        train, test = sets[lang]
        words = lists[lang]
        cases = positions(test, words)
        print(f"\n== {lang}: {len(train)} sentences to count pairs on, {len(test)} held out, {len(cases)} positions scored")
        from_words = table_from_words(words)
        from_pairs = table_from_pairs(train, words)
        score("table: word list", cases, lambda done, last: first_chars(from_words.get(last, [])))
        score("table: Tatoeba word pairs", cases, lambda done, last: first_chars(from_pairs.get(last, [])))
        score("table: pairs, then word list", cases,
              lambda done, last: first_chars(from_pairs.get(last, []) + from_words.get(last, [])))
        for name, model in models.items():
            score(f"model: {name}", cases, lambda done, last: model.next_chars(done))
    print("\n== speed, one next-token call at 20 tokens of context, on this PC")
    for name in models:
        for threads in (1, 4):
            model = Model(name, threads)
            feed = {"input_ids": np.array([list(range(1000, 1020))], dtype=np.int64)}
            model.session.run(None, feed)
            t = time.perf_counter()
            for _ in range(20):
                model.session.run(None, feed)
            print(f"  {name:12} {threads} thread{'s' if threads > 1 else ''}: {1000 * (time.perf_counter() - t) / 20:6.1f} ms")


if __name__ == "__main__":
    {"export": export, "eval": evaluate}[sys.argv[1]]()
