"""Validate the CURRENT Coordinator schemas and, when available, actual Kafka test outputs."""
import copy
import json
from decimal import Decimal
from pathlib import Path
from jsonschema import Draft202012Validator, FormatChecker
from referencing import Registry, Resource

ROOT = Path(__file__).resolve().parents[1]
CONTRACTS = ROOT / 'contracts'
def load(path):
    return json.loads(path.read_text(encoding='utf-8-sig'), parse_float=Decimal)
names = ['offer-created', 'trade-executed', 'exchange-rate-updated']
schemas = {name: load(CONTRACTS/'events'/(name+'.schema.json')) for name in names}
registry = Registry().with_resources(((CONTRACTS/'events'/(name+'.schema.json')).as_uri(), Resource.from_contents(schema)) for name,schema in schemas.items())
uid = '11111111-1111-4111-8111-111111111111'
other = '22222222-2222-4222-8222-222222222222'
time = '2026-09-28T18:00:00Z'
examples = {
    'offer-created': dict(eventId=uid,offerId=uid,side='BUY',participantId=other,price=Decimal('30.00'),createdAt=time),
    'trade-executed': dict(eventId=uid,tradeId=uid,buyOfferId=uid,sellOfferId=other,buyerId=uid,sellerId=other,price=Decimal('25.00'),executedAt=time),
    'exchange-rate-updated': dict(eventId=uid,tradeId=uid,pair='PIGGY-USD',rate=Decimal('25.00'),occurredAt=time)
}
checks = 0
for name,schema in schemas.items():
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema,format_checker=FormatChecker())
    validator.validate(examples[name]); checks += 1
    for bad in [dict(examples[name],eventId='invalid'),dict(examples[name],unexpected=True)]:
        assert not validator.is_valid(bad), name
        checks += 1
    bad = copy.deepcopy(examples[name]); bad.pop('eventId')
    assert not validator.is_valid(bad); checks += 1
    money = 'rate' if name == 'exchange-rate-updated' else 'price'
    for invalid in [Decimal('0'),Decimal('-1'),Decimal('1.001'),'25.00']:
        assert not validator.is_valid(dict(examples[name],**{money:invalid})); checks += 1
coordinator = load(CONTRACTS/'coordinator.schema.json')
Draft202012Validator.check_schema(coordinator)
registry = registry.with_resource((CONTRACTS/'coordinator.schema.json').as_uri(),Resource.from_contents(coordinator))
v = Draft202012Validator({'$ref':(CONTRACTS/'coordinator.schema.json').as_uri()},registry=registry,format_checker=FormatChecker())
v.validate(examples['offer-created']); checks += 1
assert not v.is_valid(examples['trade-executed']); checks += 1
captured = 0
for name in names:
    path = ROOT/'target'/'coordinator-contract-examples'/(name+'.json')
    if path.exists():
        Draft202012Validator(schemas[name],format_checker=FormatChecker()).validate(load(path))
        captured += 1
print(f'OK: 4 schemas atuais, {checks} casos estáticos e {captured} mensagens capturadas no teste Kafka.')
if captured != 3:
    print('Execute CoordinatorKafkaIntegrationTest para validar também as mensagens reais.')
