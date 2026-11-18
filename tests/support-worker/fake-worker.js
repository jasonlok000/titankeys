import { hash, SupportError } from "../../server/support/src/play.js";
import { createWorker } from "../../server/support/src/worker.js";

export default createWorker({
    async verify(_, account, purchase) {
        const token = "offline-seven-unit-tip";
        if (account !== await hash("f".repeat(64)) || purchase.purchaseToken !== token ||
            purchase.kind !== "tip" || purchase.productId !== "support_tip_unit") {
            throw new SupportError(422, "purchase_unverified");
        }
        return {
            productId: purchase.productId, cents: 700, nominalCents: 700,
            tokenHash: await hash(token), orderHash: await hash("offline-order"),
        };
    },
    async voided() { return {}; },
});
