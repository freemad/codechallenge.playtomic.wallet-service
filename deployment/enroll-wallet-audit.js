// mongosh, on the selected wallet database, while all wallet writers are stopped.
// AUDIT_WALLET_ID, AUDIT_CURRENCY and AUDIT_OPENING must come from the approved opening evidence.
const id = process.env.AUDIT_WALLET_ID;
const currency = process.env.AUDIT_CURRENCY;
const amount = process.env.AUDIT_OPENING;
if (!id || !currency || !amount || !/^-?\d+(\.\d+)?$/.test(amount)) throw new Error('Explicit verified opening required');
const session = db.getMongo().startSession();
try {
    session.withTransaction(() => {
        const database = session.getDatabase(db.getName());
        const openingBalance = NumberDecimal(amount);
        const wallet = database.wallets.findOne({_id: id, currency, balance: {$eq: openingBalance, $type: 'decimal'}});
        if (!wallet) throw new Error('Verified opening does not match the persisted wallet');
        const now = db.runCommand({hello: 1}).localTime;
        const nextCutoff = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + 1));
        database.wallet_audit_schedule.insertOne({_id: id, currency, openingBalance,
            openingSequence: wallet.ledgerSequence ?? NumberLong(0), nextCutoff,
            availableAt: nextCutoff, blocked: false});
    }, {readConcern: {level: 'snapshot'}, writeConcern: {w: 'majority'}});
} finally {
    session.endSession();
}
