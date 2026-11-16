"""onnx2torch indexes with a list for multi-axis slices. Since PyTorch 2.9 a list index means something else, which
silently changes the model's output. This makes it a tuple. Usage: python fix_onnx2torch.py"""
import os

import onnx2torch

FIXES = {
    'slice.py': [('x = x[pos_axes_slices]', 'x = x[tuple(pos_axes_slices)]'), ('x = x[neg_axes_slices]', 'x = x[tuple(neg_axes_slices)]')],
    # ONNX's CumSum adds forwards unless told otherwise. The converter assumed the reverse.
    'cumsum.py': [("node_attributes.get('reverse', 1)", "node_attributes.get('reverse', 0)")],
}
for name, swaps in FIXES.items():
    path = os.path.join(os.path.dirname(onnx2torch.__file__), 'node_converters', name)
    text = open(path, encoding='utf-8').read()
    fixed = text
    for old, new in swaps:
        fixed = fixed.replace(old, new)
    if fixed != text:
        open(path, 'w', encoding='utf-8').write(fixed)
    print(name, 'fixed' if fixed != text else 'already fixed')
