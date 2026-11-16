"""The small swipe encoder from the pilot, and FUTO's resampling. Shared by the pilot and the GPU experiments."""
import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F

ROWS = ['qwertyuiop', 'asdfghjkl', 'zxcvbnm']
LETTERS = 'abcdefghijklmnopqrstuvwxyz'
INDEX = {c: i for i, c in enumerate(LETTERS)}


def titan_grid():
    grid = {}
    for r, row in enumerate(ROWS):
        for c, ch in enumerate(row):
            grid[ch] = ((c + (1 if r == 2 else 0) + .5) / 10, (r + .5) / 3)
    return grid


def featurize(x, y, t):
    """FUTO's resampling: about 60 Hz in time, then 64 points evenly by index, clipped to the keyboard."""
    x, y, t = np.asarray(x, dtype=float), np.asarray(y, dtype=float), np.asarray(t, dtype=float)
    duration = t[-1] - t[0]
    if len(x) < 2 or duration <= 1e-12:
        hx, hy = np.array([x[0], x[-1]]), np.array([y[0], y[-1]])
    else:
        n = max(2, int(np.rint(duration / (1000 / 60))) + 1)
        grid = np.minimum(t[0] + np.arange(n) * (duration / (n - 1)), t[-1])
        idx = np.searchsorted(t, grid, 'left')
        hx, hy = np.zeros(n), np.zeros(n)
        for i, (g, j) in enumerate(zip(grid, idx)):
            if j == 0:
                hx[i], hy[i] = x[0], y[0]
            elif j >= len(t):
                hx[i], hy[i] = x[-1], y[-1]
            else:
                seg = t[j] - t[j - 1]
                f = (g - t[j - 1]) / seg if seg > 1e-12 else 0
                hx[i], hy[i] = x[j - 1] + f * (x[j] - x[j - 1]), y[j - 1] + f * (y[j] - y[j - 1])
    pos = np.linspace(0, 1, 64) * (len(hx) - 1)
    i0 = pos.astype(int)
    i1 = np.minimum(i0 + 1, len(hx) - 1)
    f = pos - i0
    rx = np.clip(hx[i0] + f * (hx[i1] - hx[i0]), 0, 1)
    ry = np.clip(hy[i0] + f * (hy[i1] - hy[i0]), 0, 1)
    return np.stack([rx, ry], 0).astype(np.float32)


class Block(nn.Module):
    def __init__(self, ch, dilation):
        super().__init__()
        self.conv = nn.Conv1d(ch, ch, 5, padding=2 * dilation, dilation=dilation)
        self.norm = nn.GroupNorm(8, ch)
        self.conv2 = nn.Conv1d(ch, ch, 1)

    def forward(self, x):
        return x + self.conv2(F.gelu(self.norm(self.conv(x))))


class BigNet(nn.Module):
    """A larger version of the pilot model, for training from scratch on FUTO's million swipes."""

    def __init__(self, ch=256, first=(1, 2, 3), second=(1, 2, 4, 8, 1, 2, 4, 8)):
        super().__init__()
        self.config = {'ch': ch, 'first': list(first), 'second': list(second)}
        self.head = nn.Conv1d(4, ch, 5, padding=2)
        self.a = nn.Sequential(*[Block(ch, d) for d in first])
        self.down = nn.Conv1d(ch, ch, 4, stride=2, padding=1)
        self.b = nn.Sequential(*[Block(ch, d) for d in second])
        self.out = nn.Conv1d(ch, 27, 1)

    def forward(self, f):
        delta = torch.zeros_like(f)
        delta[:, :, 1:] = f[:, :, 1:] - f[:, :, :-1]
        h = F.gelu(self.head(torch.cat([f, delta * 10], 1)))
        return F.log_softmax(self.out(self.b(self.down(self.a(h)))), 1).permute(2, 0, 1)


def augment(f, generator=None):
    """Whole-stroke changes like FUTO trains with: small shifts, scaling, a slight turn and noise. f: batch x 2 x 64."""
    b, dev = f.shape[0], f.device
    rand = lambda *s: torch.rand(*s, device=dev, generator=generator)
    scale = 1 + (rand(b, 1, 1) - .5) * .2
    angle = (rand(b, 1) - .5) * .12
    shift = (rand(b, 2, 1) - .5) * torch.tensor([.06, .1], device=dev).view(1, 2, 1)
    centre = f.mean(2, keepdim=True)
    x, y = f[:, 0] - centre[:, 0], f[:, 1] - centre[:, 1]
    # Rotate in key proportions: a key is 0.1 wide and 0.333 high in these units.
    kx, ky = x * 10, y * 3
    c, s = torch.cos(angle), torch.sin(angle)
    rx, ry = (c * kx - s * ky) / 10, (s * kx + c * ky) / 3
    out = torch.stack([rx, ry], 1) * scale + centre + shift
    out = out + torch.randn(out.shape, device=dev, generator=generator) * .004
    return out.clamp(0, 1)


class Net(nn.Module):
    """The pilot's model. Its saved weights load into this unchanged."""

    def __init__(self, ch=96):
        super().__init__()
        self.head = nn.Conv1d(4, ch, 5, padding=2)
        self.a = nn.Sequential(Block(ch, 1), Block(ch, 2), Block(ch, 3))
        self.down = nn.Conv1d(ch, ch, 4, stride=2, padding=1)
        self.b = nn.Sequential(Block(ch, 1), Block(ch, 2), Block(ch, 4), Block(ch, 8), Block(ch, 1))
        self.out = nn.Conv1d(ch, 27, 1)

    def forward(self, f):
        """f: batch x 2 x 64. Returns frames x batch x 27 log chances, with the blank last."""
        delta = torch.zeros_like(f)
        delta[:, :, 1:] = f[:, :, 1:] - f[:, :, :-1]
        h = F.gelu(self.head(torch.cat([f, delta * 10], 1)))
        return F.log_softmax(self.out(self.b(self.down(self.a(h)))), 1).permute(2, 0, 1)
