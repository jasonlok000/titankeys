#!/usr/bin/env python3
"""Puts swipe engine 2.0's model files into a TitanKeys tree and writes their manifest, which ModelAssets.java checks.
Also copies ONNX Runtime's Java classes and arm64 libraries.
Usage: make_swipe2.py tree encoder.onnx swipe-ranker.txt predictor-dir ort-dir"""
import hashlib
import os
import shutil
import sys

tree, encoder, ranker, predictor, ort = sys.argv[1:6]
dest = os.path.join(tree, 'android', 'assets', 'swipe2')
os.makedirs(os.path.join(dest, 'predictor'), exist_ok=True)
files = {
    'ck-titan.onnx': encoder,
    'swipe-ranker.txt': ranker,
    'predictor/model-int8pc.onnx': os.path.join(predictor, 'model-int8pc.onnx'),
    'predictor/lm-vocab.txt': os.path.join(predictor, 'lm-vocab.txt'),
    'predictor/lm-merges.txt': os.path.join(predictor, 'lm-merges.txt'),
    'predictor/lm-info.txt': os.path.join(predictor, 'lm-info.txt'),
}
lines = ['titankeys-swipe-models-v2']
for name, source in files.items():
    target = os.path.join(dest, name)
    shutil.copyfile(source, target)
    digest = hashlib.sha256(open(target, 'rb').read()).hexdigest()
    lines.append(f'{digest} {os.path.getsize(target)} {name}')
with open(os.path.join(dest, 'manifest.sha256'), 'w', encoding='utf-8', newline='\n') as f:
    f.write('\n'.join(lines) + '\n')
libs = os.path.join(tree, 'android', 'libs')
os.makedirs(libs, exist_ok=True)
shutil.copyfile(os.path.join(ort, 'onnxruntime-android-1.20.0-classes.jar'), os.path.join(libs, 'onnxruntime-android-1.20.0.jar'))
for so in ('libonnxruntime.so', 'libonnxruntime4j_jni.so'):
    shutil.copyfile(os.path.join(ort, 'jni', 'arm64-v8a', so), os.path.join(tree, 'android', 'jniLibs', 'arm64-v8a', so))
print('\n'.join(lines))
