"""Turns half-precision weights that are cast at run time into plain weights, so the model can be converted for training.
Usage: python unpack_onnx.py in.onnx out.onnx"""
import sys

import numpy as np
import onnx
from onnx import numpy_helper

source, target = sys.argv[1:3]
model = onnx.load(source)
graph = model.graph
inits = {i.name: i for i in graph.initializer}
keep, added = [], []
for node in graph.node:
    if node.op_type == 'Cast' and len(node.input) == 1 and node.input[0] in inits:
        to = next(a.i for a in node.attribute if a.name == 'to')
        value = numpy_helper.to_array(inits[node.input[0]]).astype(onnx.helper.tensor_dtype_to_np_dtype(to))
        added.append(numpy_helper.from_array(value, node.output[0]))
        continue
    keep.append(node)
used = {name for node in keep for name in node.input}
remaining = [i for i in graph.initializer if i.name in used]
del graph.node[:]
graph.node.extend(keep)
del graph.initializer[:]
graph.initializer.extend(remaining + added)
onnx.checker.check_model(model)
onnx.save(model, target)
print('unpacked', len(added), 'weights; nodes', len(keep))
