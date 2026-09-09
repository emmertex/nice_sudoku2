#!/usr/bin/env python3
"""Check shipped puzzle data, icon resources, and release asset consistency."""
import json
from pathlib import Path
import re
import struct

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'web/src/jsMain/resources'
count = 0
for path in (RES / 'puzzles').glob('*.json'):
    ids = set()
    for puzzle in json.loads(path.read_text())['puzzles']:
        count += 1
        givens, solution = puzzle['givens'], puzzle['solution']
        assert puzzle['puzzleId'] not in ids, (path, 'duplicate ID')
        ids.add(puzzle['puzzleId'])
        assert len(givens) == len(solution) == 81
        assert set(givens) <= set('0123456789.')
        assert all(a in '0.' or a == b for a, b in zip(givens, solution))
        units = [solution[i*9:i*9+9] for i in range(9)] + [solution[i::9] for i in range(9)]
        units += [''.join(solution[(r+j)*9+c:(r+j)*9+c+3] for j in range(3))
                  for r in range(0,9,3) for c in range(0,9,3)]
        assert all(set(unit) == set('123456789') for unit in units)
for path in (RES / 'languages').glob('*.json'):
    json.loads(path.read_text())
for icon in json.loads((RES / 'manifest.json').read_text())['icons']:
    data = (RES / icon['src'].lstrip('/')).read_bytes()
    assert data[:8] == b'\x89PNG\r\n\x1a\n'
    width, height = struct.unpack('>II', data[16:24])
    assert f'{width}x{height}' == icon['sizes']
assert struct.unpack('<HHH', (RES / 'favicon.ico').read_bytes()[:6]) == (0, 1, 3)
version = re.search(r'APP_VERSION = "v([\d.]+)"', (ROOT / 'web/src/jsMain/kotlin/AppUtils.kt').read_text())[1]
assert (RES / 'CHANGELOG.md').read_text().startswith(f'# v{version} - ')
assert f'/web.js?v={version}' in (RES / 'index.html').read_text()
assert f'/web.js?v={version}' in (RES / 'service-worker.js').read_text()
print(f'{count} puzzles, language JSON, manifest icons, ICO sizes and release references passed.')
