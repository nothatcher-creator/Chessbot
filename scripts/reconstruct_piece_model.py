#!/usr/bin/env python3
import base64
import gzip
import hashlib
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CHUNKS = ROOT / 'modelsrc' / 'piece_mlp_q4'
HEAD_PATCH = ROOT / 'modelsrc' / 'lichess_head_v1.b64'
OUT = ROOT / 'app' / 'src' / 'main' / 'assets' / 'piece_mlp.bin'
EXPECTED_TRANSPORT_SHA256 = 'bbdefc21783ea0f72f5b93bcf165815c7044b246e4aef1fdfea043f3b46eb7cd'
EXPECTED_HEAD_PATCH_SHA256 = 'a765802d20387a496333f80f19919cfd2caf3f2f2846517fe54a0b72177339c7'
EXPECTED_MODEL_SHA256 = '2e6579341f341e0e53f848981ff410a4ccb2e2d996ae608ef534f95c79f2c0d8'

encoded = b''.join(p.read_bytes().strip() for p in sorted(CHUNKS.glob('part-*.b64')))
compressed = base64.b64decode(encoded, validate=True)
if hashlib.sha256(compressed).hexdigest() != EXPECTED_TRANSPORT_SHA256:
    raise SystemExit('piece model transport hash mismatch')
q = gzip.decompress(compressed)
if q[:5] != b'Q4LP1':
    raise SystemExit('bad quantized model magic')
inp, h1, h2, out = struct.unpack_from('<4i', q, 5)
off = 21
result = bytearray(b'PMLP2') + struct.pack('<4i', inp, h1, h2, out)
for rows, cols in ((h1, inp), (h2, h1), (out, h2)):
    scales = struct.unpack_from(f'<{rows}f', q, off)
    off += 4 * rows
    count = rows * cols
    packed_len = (count + 1) // 2
    packed = memoryview(q)[off:off + packed_len]
    off += packed_len
    biases = struct.unpack_from(f'<{rows}e', q, off)
    off += 2 * rows
    idx = 0
    for r in range(rows):
        scale = scales[r]
        for _ in range(cols):
            byte = packed[idx // 2]
            nibble = (byte & 0x0f) if idx % 2 == 0 else (byte >> 4)
            if nibble >= 8:
                nibble -= 16
            result += struct.pack('<f', float(nibble) * scale)
            idx += 1
    for bias in biases:
        result += struct.pack('<f', float(bias))
if off != len(q):
    raise SystemExit(f'unexpected trailing model bytes: {len(q) - off}')

# Fine-tuned classifier head trained against the real Lichess wood-board style.
head_compressed = base64.b64decode(HEAD_PATCH.read_bytes().strip(), validate=True)
if hashlib.sha256(head_compressed).hexdigest() != EXPECTED_HEAD_PATCH_SHA256:
    raise SystemExit('Lichess head patch hash mismatch')
head = gzip.decompress(head_compressed)
if head[:4] != b'LHP1':
    raise SystemExit('bad Lichess head patch magic')
rows, cols = struct.unpack_from('<2i', head, 4)
if (rows, cols) != (out, h2):
    raise SystemExit(f'unexpected Lichess head dimensions: {(rows, cols)}')
head_bytes = head[12:]
expected_head_bytes = 4 * (rows * cols + rows)
if len(head_bytes) != expected_head_bytes:
    raise SystemExit('truncated Lichess head patch')
head_offset = 21 + 4 * (h1 * inp + h1 + h2 * h1 + h2)
result[head_offset:head_offset + expected_head_bytes] = head_bytes

sha = hashlib.sha256(result).hexdigest()
if sha != EXPECTED_MODEL_SHA256:
    raise SystemExit(f'expanded model hash mismatch: {sha}')
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_bytes(result)
print(f'wrote {OUT} ({len(result)} bytes, sha256={sha})')
