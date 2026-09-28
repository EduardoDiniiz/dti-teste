"""Validação estática dos contratos; não executa a aplicação."""
import copy
import json
from pathlib import Path
from jsonschema import Draft202012Validator, FormatChecker
from referencing import Registry, Resource
from openapi_spec_validator import validate_spec

ROOT = Path(__file__).resolve().parents[1]
C = ROOT / 'contracts'
docs = {p: json.loads(p.read_text(encoding='utf-8')) for p in C.rglob('*.json')}
registry = Registry().with_resources((p.as_uri(), Resource.from_contents(d)) for p,d in docs.items() if '$schema' in d)
checks = 0
def check(schema, value, valid=True, base=None):
    global checks
    # Referências relativas do Coordinator são resolvidas a partir do arquivo.
    if base:
        schema = {'$ref':base.as_uri()}
    validator = Draft202012Validator(schema, registry=registry, format_checker=FormatChecker())
    errors = list(validator.iter_errors(value))
    assert bool(errors) != valid, str(errors[:1]) if valid else 'Caso inválido aceito'
    checks += 1

for p,d in docs.items():
    if '$schema' in d:
        Draft202012Validator.check_schema(d)
        for example in d.get('examples',[]):
            check(d, example)
            bad = copy.deepcopy(example); bad['schemaVersion']=2; check(d,bad,False)
            bad = copy.deepcopy(example); bad['eventId']='invalid'; check(d,bad,False)
            bad = copy.deepcopy(example); bad['unexpected']=True; check(d,bad,False)

for party in ['buyer','seller']:
    d = docs[C / (party+'.openapi.json')]
    validate_spec(d)
    schemas = d['components']['schemas']
    def s(name):
        return {**schemas[name], 'components': d['components']}
    for item in d['paths'].values():
        for op in item.values():
            media = op.get('requestBody',{}).get('content',{})
            media = list(media.values()) + [m for r in op['responses'].values() for m in r.get('content',{}).values()]
            for m in media:
                if 'example' in m:
                    name = m['schema']['$ref'].rsplit('/',1)[1]
                    check(s(name),m['example'])
    request = next(iter(d['paths'].values()))['post']['requestBody']['content']['application/json']['example']
    for value in ['0','0.00','-1','1.001','1e2',1.5,'1000000000000']:
        check(s('CreateOffer'),{**request,'priceUsd':value},False)
    for value in ['0.01','20','20.00','999999999999.99']:
        check(s('CreateOffer'),{**request,'priceUsd':value})
    check(s('CreateOffer'),{**request,'quantity':1},False)
    pending = {**request,'createdAt':'2026-09-28T18:00:00Z','status':'PENDING'}
    check(s('Offer'), pending)
    check(s('Offer'), {**pending,'status':'EXECUTED'},False)
    check(s('Offer'), {**pending,'status':'REJECTED'},False)

coordinator = C / 'coordinator.schema.json'
for filename in ['buy-offer-created','sell-offer-created']:
    check({},docs[C/'examples'/(filename+'.json')],base=coordinator)
check({},docs[C/'examples'/'transaction-completed.json'],False,base=coordinator)
for entry in docs[C/'kafka-topology.json']['events'].values():
    assert (C/entry['schema']).is_file(), entry
print(f'OK: 2 OpenAPI, 6 JSON Schemas, referências, topologia e {checks} casos de payload.')
