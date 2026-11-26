const members = [1, 2, 3].map((n, i) => ({_id: i, host: `mongo-${n}:27017`}));
const deadline = Date.now() + 90000;
while (Date.now() < deadline) {
    const fresh = [];
    for (const member of members) {
        let admin;
        try {
            admin = new Mongo(`mongodb://${member.host}/?directConnection=true&serverSelectionTimeoutMS=1000`).getDB('admin');
            const status = admin.runCommand({replSetGetStatus: 1});
            if (status.set !== 'wallet-rs') throw new Error('Unexpected replica set');
            const liveVoters = status.members.filter(node => node.health === 1 &&
                (node.stateStr === 'PRIMARY' || node.stateStr === 'SECONDARY'));
            if (status.myState === 1 && liveVoters.length >= status.writeMajorityCount) quit(0);
        } catch (err) {
            if (err.codeName === 'NotYetInitialized') fresh.push(admin);
            else if (!(err.name.includes('Network') || err.name.includes('ServerSelection'))) throw err;
        }
    }
    if (fresh.length === members.length) fresh[0].runCommand({replSetInitiate: {_id: 'wallet-rs', members}});
    sleep(1000);
}
throw new Error('No writable wallet-rs majority before startup deadline');