# Legal texts

Drafts written against the Colombian rules the store has to follow. They are **not reviewed by a
lawyer yet**, which is one of the open decisions in the [master plan](../master-plan.md). Nothing
here should be published until it is.

| File | Covers |
|---|---|
| [politica-de-tratamiento-de-datos.md](politica-de-tratamiento-de-datos.md) | Ley 1581 de 2012 and Decreto 1377 de 2013: purposes, rights, channel, retention |
| [aviso-de-privacidad.md](aviso-de-privacidad.md) | The short notice shown when data is collected |

## What the code already does

| Duty | Where |
|---|---|
| Prior, informed and specific consent, with proof | `consents` table: one row per decision, with the moment, the channel, the policy version and a hash of the address |
| Separate purposes | `ConsentPurpose`: handling the account is not marketing, and email is not WhatsApp |
| Right to know and to a copy | `GET /api/me/export` |
| Right to revoke | `PUT /api/me/consents` |
| Right to deletion | `DELETE /api/me`, which anonymises rather than erases where the law requires the record to be kept |
| Policy version on every consent | `app.legal.privacy-policy-version` |

## Still to do before launch

- Legal review of both texts, and of the terms of sale.
- The rights channel with a `radicado` and the 10 and 15 business-day answers, which arrives with the
  support module.
- The pages served in the storefront, which arrives with the content module.
- Decide whether the company must register its databases with the RNBD. The threshold is based on
  total assets, so it depends on the accountant, not on the code.
- Disclose the international transfer: the servers are outside Colombia.
