try {
    db.adminCommand({replSetGetStatus: 1});
} catch (error) {
    if (error.codeName !== "NotYetInitialized") {
        throw error;
    }
    assert.commandWorked(rs.initiate({
        _id: "wallet-rs",
        members: [
            {_id: 0, host: "mongo-1:27017"},
            {_id: 1, host: "mongo-2:27017"},
            {_id: 2, host: "mongo-3:27017"}
        ]
    }));
}

const deadline = Date.now() + 60000;
while (Date.now() < deadline) {
    const {members} = db.adminCommand({replSetGetStatus: 1});
    if (members.some(member => member.stateStr === "PRIMARY")
            && members.filter(member => member.stateStr === "SECONDARY").length === 2) {
        quit(0);
    }
    sleep(1000);
}
throw new Error("Timed out waiting for a primary and two secondaries");
