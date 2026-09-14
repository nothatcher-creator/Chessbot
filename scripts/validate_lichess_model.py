#!/usr/bin/env python3
import base64
import gzip
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODEL = ROOT / 'app' / 'src' / 'main' / 'assets' / 'piece_mlp.bin'
FIXTURE = ROOT / 'testdata' / 'lichess_features.b64'
CLASSES = ['EMPTY', 'WP', 'WN', 'WB', 'WR', 'WQ', 'WK', 'BP', 'BN', 'BB', 'BR', 'BQ', 'BK']


def load_model(path: Path):
    data = path.read_bytes()
    if data[:5] != b'PMLP2':
        raise SystemExit('bad model magic')
    inp, h1, h2, out = struct.unpack_from('<4i', data, 5)
    off = 21

    def floats(count):
        nonlocal off
        values = struct.unpack_from(f'<{count}f', data, off)
        off += 4 * count
        return values

    w1 = floats(h1 * inp); b1 = floats(h1)
    w2 = floats(h2 * h1); b2 = floats(h2)
    w3 = floats(out * h2); b3 = floats(out)
    if off != len(data):
        raise SystemExit('trailing model bytes')
    return inp, h1, h2, out, w1, b1, w2, b2, w3, b3


def dense_relu(x, weights, bias, rows):
    cols = len(x)
    result = []
    for row in range(rows):
        total = bias[row]
        base = row * cols
        for col, value in enumerate(x):
            total += weights[base + col] * value
        result.append(total if total > 0.0 else 0.0)
    return result


def dense(x, weights, bias, rows):
    cols = len(x)
    result = []
    for row in range(rows):
        total = bias[row]
        base = row * cols
        for col, value in enumerate(x):
            total += weights[base + col] * value
        result.append(total)
    return result


def predict(model, features):
    inp, h1, h2, out, w1, b1, w2, b2, w3, b3 = model
    if len(features) != inp:
        raise SystemExit('bad fixture feature size')
    hidden1 = dense_relu(features, w1, b1, h1)
    hidden2 = dense_relu(hidden1, w2, b2, h2)
    logits = dense(hidden2, w3, b3, out)
    return max(range(len(logits)), key=logits.__getitem__)


model = load_model(MODEL)
raw = gzip.decompress(base64.b64decode(FIXTURE.read_bytes(), validate=True))
if raw[:4] != b'LFX1':
    raise SystemExit('bad fixture magic')
count = struct.unpack_from('<H', raw, 4)[0]
off = 6
failures = []
for index in range(count):
    expected = raw[off]
    off += 1
    features = struct.unpack_from('<512e', raw, off)
    off += 1024
    actual = predict(model, features)
    if actual != expected:
        failures.append((index, CLASSES[expected], CLASSES[actual]))
if off != len(raw):
    raise SystemExit('trailing fixture bytes')
if failures:
    for index, expected, actual in failures:
        print(f'fixture {index}: expected {expected}, got {actual}', file=sys.stderr)
    raise SystemExit(f'Lichess recognition regression failed: {len(failures)}/{count} mismatches')
print(f'Lichess recognition regression passed: {count}/{count}')
