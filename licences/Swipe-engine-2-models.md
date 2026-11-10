# Swipe engine 2.0 models and runtime

The files in `android/assets/swipe2` and the ONNX Runtime library come from these projects.

## Swipe encoder: `ck-titan.onnx`

- Source: the CTC swipe encoder of [CleverKeys](https://github.com/tribixbite/CleverKeys), which is under the GNU General Public License v3.0, the same as TitanKeys. See [`LICENSE`](../LICENSE) for the licence text.
- Changes: the weights are fine-tuned on swipes from the Titan 2 Elite touchpad, and the model only gives the letter outputs.

## Next-word model: `predictor/model-int8pc.onnx`

- Source: [DistilGPT-2](https://huggingface.co/distilbert/distilgpt2) by Hugging Face, under the Apache License 2.0. See [`AOSP-Apache-2.0.txt`](AOSP-Apache-2.0.txt) for the licence text.
- Changes: exported to ONNX to give only the chances of the next token, and the weights are stored as 8-bit numbers. The tokenizer files `lm-vocab.txt` and `lm-merges.txt` come from the same model.

## Runtime: ONNX Runtime 1.20.0

- Source: [ONNX Runtime](https://github.com/microsoft/onnxruntime) by Microsoft, under the MIT License:

```
MIT License

Copyright (c) Microsoft Corporation

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Ranker: `swipe-ranker.txt`

Decision trees made with [LightGBM](https://github.com/microsoft/LightGBM) (MIT License) from labelled TitanKeys swipes. TitanKeys reads them with its own code; LightGBM itself is not included.
