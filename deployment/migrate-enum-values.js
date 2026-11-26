// Offline mongosh migration; select the wallet database explicitly (see README).
const fields = [
    [db.wallet_operations, 'type', {TOP_UP: 1, SPEND: 2}],
    [db.wallet_operations, 'status', {PENDING: 1, REVIEW_REQUIRED: 2, RECEIPT_RECORDED: 3,
        SUCCEEDED: 4, INSUFFICIENT_FUNDS: 5, PAYMENT_REJECTED: 6, PROVIDER_UNAVAILABLE: 7, WALLET_UNAVAILABLE: 8}],
    [db.wallet_outbox, 'eventType', {WalletCredited: 1, WalletDebited: 2}]
];

// Check all collections before changing any rows. Accept already-migrated int32s on reruns.
for (const [collection, field, codes] of fields) {
    const path = '$' + field;
    const valid = {$or: [{$in: [path, Object.keys(codes)]}, {$and: [
        {$eq: [{$type: path}, 'int']}, {$in: [path, Object.values(codes)]}
    ]}]};
    if (collection.findOne({$expr: {$not: [valid]}}, {_id: 1})) {
        throw new Error(`Unexpected ${collection.getName()}.${field}; resolve before migrating`);
    }
}
for (const [collection, field, codes] of fields) {
    for (const [label, code] of Object.entries(codes)) {
        collection.updateMany({[field]: label}, {$set: {[field]: NumberInt(code)}}, {writeConcern: {w: 'majority'}});
    }
}

