"""Disposable loopback demo; pass --ha to exercise crash, election and scheduled recovery."""
import argparse
import json
import os
from pathlib import Path
import subprocess as sp
import tempfile
import time
import uuid
from decimal import Decimal
from concurrent.futures import ThreadPoolExecutor

ROOT = Path(__file__).resolve().parents[1]
PROJECT = 'wallet-check-' + uuid.uuid4().hex[:12]
MONGO = 'mongo:8.3.17-noble'
SEEDS = ','.join(f'mongo-{i}:27017' for i in range(1, 4))
URI = f'mongodb://{SEEDS}/playtomic?replicaSet=wallet-rs&w=majority'
BASE = '/api/v1/wallets/'


def run(*args, check=True):
    return sp.run(args, check=check, text=True, stdout=sp.PIPE, timeout=240).stdout.strip()


def wait(test, seconds=90):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        try:
            if test():
                return
        except (sp.CalledProcessError, ValueError, KeyError):
            pass
        time.sleep(1)
    raise RuntimeError(f'Timed out waiting for {test}')


def check(ha, directory):
    override = Path(directory) / 'check.yaml'
    override.write_text('services:\n' + ''.join(f'  mongo-{i}:\n    command: ["--replSet", "wallet-rs", "--bind_ip_all", "--setParameter", "enableTestCommands=1"]\n' for i in range(1, 4)))
    compose = ['docker', 'compose', '-p', PROJECT, '-f', str(ROOT/'deployment/compose.yaml'), '-f', str(override)]
    owned = []
    if not __debug__:
        raise RuntimeError('This check requires Python assertions')

    def start(name, *args):
        result = run('docker', 'run', '-d', '--name', PROJECT+'-'+name, *args)
        owned.append(result)
        return result

    def js(expression, uri=URI):
        return run('docker', 'exec', anchor, 'mongosh', '--quiet', uri, '--eval', expression)

    def http(port, path, amount=None, currency='EUR', key='demo'):
        opts = {} if amount is None else {'method':'POST', 'headers':{'Content-Type':'application/json','Idempotency-Key':key}, 'body':'{"amount":'+amount+',"currency":"'+currency+'","provider":"stripe","creditCardNumber":"4242424242424242"}'}
        raw = js(f'(async()=>{{const r=await fetch("http://127.0.0.1:{port}{path}",{json.dumps(opts)}); print(JSON.stringify([r.status,await r.text()]));}})()', '--nodb')
        status, body = json.loads(raw)
        return status, json.loads(body, parse_float=Decimal)

    def launch(name, module, jar, *args):
        return start(name, '--network', 'container:'+anchor, '-v', f'{ROOT/module/"target"/jar}:/app.jar:ro', 'eclipse-temurin:25-jre-noble', 'java', '-XX:ActiveProcessorCount=2', '-Xmx384m', '-jar', '/app.jar', *args)

    def simulator(name, scenario):
        cid = launch(name, 'simulator', 'payment-simulator-0.0.3.jar', '--server.address=127.0.0.1', '--payment-simulator.scenario='+scenario)
        wait(lambda: http(9999, '/actuator/health')[0] in (200,404))
        return cid

    def wallet(name, port):
        cid = launch(name, 'wallet', 'exercise-wallet-0.0.3.jar', '--spring.profiles.active=local-top-up', '--server.port='+str(port), '--spring.mongodb.uri='+URI+'&appName='+name)
        wait(lambda: http(port, '/actuator/health/readiness')[0] == 200)
        return cid

    def seed(name, opening='10.00', currency='EUR'):
        js(f'db.wallets.insertOne({{_id:"{name}",balance:NumberDecimal("{opening}"),currency:"{currency}"}})')

    def post(name, port=8090, amount='5.25', currency='EUR'):
        return http(port, BASE+name+'/top-ups', amount, currency)

    def balance(name, port=8090):
        status, body = http(port, BASE+name)
        assert status == 200, body
        return body['result']['balance']

    def evidence(name, simulator_id, expected_calls, credits=1):
        assert int(js(f'db.wallet_ledger.countDocuments({{walletId:"{name}"}})')) == credits
        calls = [json.loads(line) for line in run('docker','logs',simulator_id).splitlines() if line.startswith('{')]
        assert sum(r.get('event')=='simulation.result' and r.get('operation')=='stripe.charge' for r in calls)==expected_calls

    try:
        run(*compose, 'up', '-d', 'mongo-init')
        init = run(*compose, 'ps', '-aq', 'mongo-init')
        assert run('docker','wait',init)=='0', run('docker','logs',init)
        # Keep the demo listener private: the client and both services share loopback only.
        anchor = start('client','--network',PROJECT+'_default',MONGO,'sleep','infinity')
        sim = simulator('success','SUCCESS')
        a = wallet('wallet-a',8090)
        seed('demo')
        assert balance('demo') == 10
        for _ in range(2):
            status, body = post('demo')
            assert status==200 and body['result']['status']=='succeeded'
        assert balance('demo') == Decimal('15.25')
        evidence('demo',sim,1)
        print('GET 10.00 → POST succeeded → replay → GET 15.25; one charge and posting', flush=True)
        seed('precision','9007199254740993.00')
        assert post('precision', amount='0.01')[1]['result']['resultingBalance']==Decimal('9007199254740993.01')==balance('precision')
        seed('limit'); seed('usd',currency='USD')
        for name, amount, currency in [('limit','0.001','EUR'),('limit','1.001','EUR'),('limit','1000000','EUR'),('usd','1','USD')]:
            assert post(name,amount=amount,currency=currency)[0]==400
        assert js('db.wallet_operations.countDocuments({walletId:{$in:["limit","usd"]}})')=='0'
        assert post('limit',amount='999999.990')[1]['result']['status']=='succeeded'
        evidence('limit',sim,3)
        if ha:
            b = wallet('wallet-b',8091)
            def primary():
                return js('db.hello().primary')
            for index, name in enumerate(['crash','election','scheduled']):
                seed(name)
                old = primary(); direct = 'mongodb://'+old+'/admin?directConnection=true'
                port = 8090 if name=='crash' else 8091
                app = 'wallet-a' if port==8090 else 'wallet-b'
                fp = js('const r=db.adminCommand({configureFailPoint:"failCommand",mode:{times:1},data:{failCommands:["insert"],namespace:"playtomic.wallet_ledger",appName:"'+app+'",blockConnection:true,blockTimeMS:30000}}); print(Number(r.count)+1)',direct)
                with ThreadPoolExecutor(1) as pool:
                    first = pool.submit(post,name,port)
                    js(f'db.adminCommand({{waitForFailPoint:"failCommand",timesEntered:{int(fp)},maxTimeMS:30000}})',direct)
                    assert js(f'db.wallet_operations.countDocuments({{walletId:"{name}",status:3}})')=='1'
                    if name=='election':
                        js('db.adminCommand({replSetStepDown:45,force:true})',direct)
                        wait(lambda: primary()!=old)
                    else:
                        run('docker','kill',a if name=='crash' else b)
                    try:
                        first.result()
                    except sp.CalledProcessError:
                        pass  # A lost HTTP response is expected after process death.
                if name=='scheduled':
                    worker = Path(directory) / 'worker.yaml'
                    worker.write_text(f'''services:
  wallet:
    image: eclipse-temurin:25-jre-noble
    network_mode: container:{anchor}
    ports: !reset []
    volumes:
      - "{ROOT}/wallet/target/exercise-wallet-0.0.3.jar:/wallet.jar:ro"
    entrypoint: ["java", "-XX:ActiveProcessorCount=2", "-Xmx384m", "-jar", "/wallet.jar", "--server.port=8092", "--server.address=127.0.0.1"]
''')
                    run('env', 'SPRING_MONGODB_URI='+URI, 'WALLET_PAYMENT_PROVIDERS_STRIPE_CHARGES_URI=http://127.0.0.1:9999/v1/stripe-simulator/charges', 'WALLET_AUDIT_ENABLED=false', 'WALLET_RECONCILIATION_ENABLED=true', 'WALLET_RECONCILIATION_INTERVAL=1s', 'WALLET_RECONCILIATION_RECEIPT_DELAY=1s', *compose, '-f', str(worker), 'up', '--no-build', '--pull', 'never', '-d', '--no-deps', 'wallet')
                    wait(lambda: js('db.wallet_operations.countDocuments({walletId:"scheduled",status:4})')=='1')
                    assert Decimal(js('db.wallets.findOne({_id:"scheduled"}).balance.toString()'))==Decimal('15.25')
                else:
                    def replay():
                        for candidate in (8090, 8091):
                            try:
                                if http(candidate, '/actuator/health/readiness')[0] != 200:
                                    continue
                            except sp.CalledProcessError:
                                continue
                            status, response = post(name, candidate)
                            if status == 500:
                                return False
                            assert status == 200 and response['result']['status'] == 'succeeded', response
                            assert balance(name, candidate) == Decimal('15.25')
                            return True
                        return False
                    wait(replay, 120)
                evidence(name,sim,4+index)
                print(name+': single provider call, posting and credit',flush=True)
            run(*compose,'stop','mongo-3'); run(*compose,'run','--rm','--no-deps','mongo-init')
        run('docker','rm','-f',sim); owned.remove(sim)
        reject = simulator('reject','REJECT'); seed('declined')
        if ha:
            wallet('wallet-c',8090)
        for _ in range(2):
            status, body = post('declined'); assert status==200 and body['result']['status']=='payment_rejected'
        assert balance('declined')==10; evidence('declined',reject,1,0)
        print('Provider 422 and replay: one decline, no posting or credit',flush=True)
    finally:
        for cid in reversed(owned):
            run('docker','rm','-fv',cid,check=False)
        run(*compose,'down','-v','--remove-orphans',check=False)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__); parser.add_argument('--ha',action='store_true'); args=parser.parse_args()
    os.chdir(ROOT)
    sp.run(['mvn','-q','-DskipTests','package'],check=True)
    with tempfile.TemporaryDirectory() as directory:
        check(args.ha,directory)
