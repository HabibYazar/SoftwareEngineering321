#!/usr/bin/env python3
"""Reject accidental Enterprise directories in the public Community repo."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
for forbidden in ('enterprise', 'hms-enterprise', 'private-modules', 'commercial-license'):
    assert not (root / forbidden).exists(), f'Forbidden directory in Community repo: {forbidden}'
print('Community/Enterprise top-level separation verified.')
