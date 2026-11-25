id: KB-DEMO-PAY-001
title: BI-FAST unsuccessful transaction handling
source: RELASI AI Synthetic Banking Operations Handbook
section: Payments / BI-FAST
effective-date: 2026-01-01
---

For this synthetic demonstration, first confirm the transfer date, amount, destination bank, and transaction reference with the customer. Do not ask the customer to share a PIN, password, or one-time code.

Check the mock transaction status before recommending another transfer. If the status is pending, explain that the transaction is still being processed and record the inquiry. Do not advise the customer to repeat a transfer while its status is pending.

If the mock status is failed but the account is shown as debited, create a PAYMENT_COMPLAINT case and attach the transaction reference. If the status is successful but the recipient reports no credit, create a TRANSACTION_DISPUTE case for investigation.

Record the customer communication, evidence checked, and next update commitment in the case history. Escalate an overdue case using the configured SLA process. This demonstration procedure is synthetic and does not represent a real bank's policy or payment network response.
