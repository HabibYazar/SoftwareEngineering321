#!/usr/bin/env python3
"""Structural Sprint-1 sanity check; not a substitute for an OAS schema validator."""
from pathlib import Path
import yaml

ROOT = Path(__file__).resolve().parents[1]
contract = yaml.safe_load((ROOT / 'api/openapi.yaml').read_text(encoding='utf-8'))
assert contract['openapi'].startswith('3.1.'), 'Expected OpenAPI 3.1'
assert isinstance(contract['paths'], dict) and contract['paths'], 'Missing paths'
assert contract.get('components', {}).get('schemas'), 'Missing schemas'
for path, definition in contract['paths'].items():
    for method, operation in definition.items():
        if method not in ('get', 'post', 'put', 'patch', 'delete'):
            continue
        assert operation.get('operationId'), f'Missing operationId {method} {path}'
        assert operation.get('responses'), f'Missing responses {method} {path}'
try:
    from openapi_spec_validator import validate_spec
except ImportError:
    print('OpenAPI YAML parsed; structural checks passed. Install openapi-spec-validator for full 3.1 validation.')
else:
    validate_spec(contract)
    print('OpenAPI 3.1 schema and structural validation passed.')
