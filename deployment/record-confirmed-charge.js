// mongosh, on the selected wallet replica-set database. See docs/run-wallet.md.
// Records evidence only. Never calls the provider or writes a wallet balance.
const {OPERATION_ID: operationId, PROVIDER_RECEIPT: receipt, WALLET_ID: walletId,
    VERIFIED_AMOUNT: amount, VERIFIED_CURRENCY: currency, CASE_REFERENCE: caseRef} = process.env;
if (![operationId, receipt, walletId, amount, currency, caseRef].every(v => v && v.trim())) {
    throw new Error('Explicit verified operation, receipt, wallet, amount, currency and case reference required');
}
const session = db.getMongo().startSession();
try {
    session.withTransaction(() => {
        const database = session.getDatabase(db.getName());
        const match = {_id: operationId, walletId, provider: 'stripe', currency, amount: NumberDecimal(amount), type: 1};
        const op = database.wallet_operations.findOne(match);
        if (!op) throw new Error('Evidence does not match the stored top-up');
        const existing = database.wallet_payment_receipts.findOne({_id: receipt});
        if (existing && existing.operationId === operationId && [3, 4].includes(Number(op.status))) return;
        if (existing || Number(op.status) !== 2) throw new Error('Conflicting receipt or operation not in review');
        const now = new Date();
        database.wallet_payment_receipts.insertOne({_id: receipt, operationId, recordedAt: now, caseReference: caseRef});
        const result = database.wallet_operations.updateOne({...match, status: 2}, {$set: {status: NumberInt(3), updatedAt: now}});
        if (result.modifiedCount !== 1) throw new Error('Operation changed during settlement');
    }, {readConcern: {level: 'snapshot'}, writeConcern: {w: 'majority'}});
} finally {
    session.endSession();
}
