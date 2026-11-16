@echo off
rem Downloads FUTO's public swipes, prepares them, trains from scratch and judges the result. Writes overnight.log.
rem Run it on the GPU machine, from the folder that holds these scripts and a venv with their packages.
cd /d "%~dp0"
if not exist data mkdir data
if not exist data\train.jsonl curl.exe -sL -o data\train.jsonl https://huggingface.co/datasets/futo-org/swipe.futo.org/resolve/main/train.jsonl
if not exist data\qwerty.json curl.exe -sL -o data\qwerty.json https://huggingface.co/datasets/futo-org/swipe.futo.org/resolve/main/swipe-5/layouts/qwerty.json
if not exist data\futo.npz venv\Scripts\python.exe prep_futo.py data\qwerty.json data\futo.npz data\train.jsonl
venv\Scripts\python.exe -W ignore train_scratch.py data\futo.npz scratch 7
echo finished
