# Research: console repair and gaming retail domain (Colombia)

> Research notes, September 2026. Several official pages (DIAN, Decreto 1413/2018, Back Market help, CeX, GameStop, OEM repair pages) were unreachable during research; those items are flagged **verify**. Prices are public shop prices at research time and change often.

## A. Repair domain model

**Entities (fields in parentheses)**

- **Customer** (id, name, cédula/NIT, phone/WhatsApp, email, address, dataConsentAt, storeCreditBalance).
- **Device** (id, customerId, platform, family [PS5/PS4/Series X/S/One/Switch/Lite/OLED/Switch 2/retro], modelNumber [e.g. CFI-1215A, 1882, HAC-001(-01)], serial, region, storage, firmware, color, purchaseSource, notes). Model number drives part compatibility (see B).
- **RepairTicket** (number, customerId, deviceId, channel [WALK_IN/DROP_OFF/MAIL_IN/HOME_PICKUP], tier [STANDARD/EXPRESS], status, priority, reportedIssue, intakeChecklist (accessories received, cosmetic condition, powers on?, prior repair signs, liquid damage), photos[], credentials (encrypted; RepairShopr uses a "Password Vault"), termsAcceptedAt + signature, dataDisclaimerAccepted, technicianId, promisedDate, receivedAt, deliveredAt, warrantyOfTicketId (for re-repairs), invoiceId). RepairShopr's ticket also carries issueType, dueDate, SLA and a read-only billingStatus (Non-Billable / Invoice Required / Invoiced / Partially Invoiced).
- **StatusHistory** (ticketId, from, to, actorId, at, note, notifiedVia).
- **Diagnosis** (ticketId, findings, rootCause, repairable bool, estimatedMinutes, diagnosticFeeApplies bool, publicNote vs internalNote; RepairDesk separates "diagnostic notes viewable by both parties" from internal notes).
- **Quote** (ticketId, version, lines[], subtotal, iva, total, expiresAt, status [SENT/APPROVED/DECLINED/EXPIRED], decidedAt, decidedVia [portal/WhatsApp/in-store], depositRequired). RepairShopr locks line items once approved, can "Auto Expire after X days", and converts approved quotes to invoices.
- **QuoteLine** (quoteId, type [PART/LABOR/FEE/SHIPPING], productId/sku, description, qty, unitPrice, taxRate).
- **PartUsage** (ticketId, productId, qty, unitCost, reservedAt, consumedAt, backorderedAt, purchaseOrderId): the inventory link: reserve on approval, consume when QA passes, release on decline.
- **LaborEntry** (ticketId, technicianId, minutes, rate, billable); RepairShopr timers round to 5/15/30/60 min.
- **Technician** (skills[platforms], hourlyRate, dailyCapacityMinutes, active).
- **QAChecklist** (ticketId, items[], passedAt, testerId); RepairShopr "worksheets" can be cloned as pre-diagnostic / post-diagnostic / QA.
- **RepairWarranty** (ticketId, months, coversPart, coversLabor, startsAt=deliveredAt, waived bool, waiverSignedAt). **WarrantyClaim** (originalTicketId, newTicketId, sameIssue bool, outcome).
- **Appointment** (customerId, type [DROP_OFF/PICKUP/HOME_VISIT], slotStart, slotEnd, ticketId).
- **Shipment** (ticketId, direction [INBOUND/OUTBOUND], carrier, tracking, labelUrl, insuredValue, packagingInstructionsSentAt). RepairDesk generates inbound labels via ShipStation and keeps tracking on the ticket.
- **Loaner** (deviceId, ticketId, lentAt, dueBack, deposit).
- **Notification** (ticketId, event, channel [WHATSAPP/EMAIL/SMS], template, sentAt). **Invoice** (ticketId/orderId, type [FACTURA_ELECTRONICA/POS], cufe, total).

**State machine**

```
REQUESTED (web/WhatsApp/mail-in form) -> CHECKED_IN (receipt issued, photos, signature)
CHECKED_IN -> DIAGNOSING -> QUOTED -+-> APPROVED -> [WAITING_PARTS] -> IN_REPAIR -> QA
                                    +-> DECLINED -> READY (diag fee only)
                                    +-> EXPIRED (timeout) -> reminder -> DECLINED
DIAGNOSING -> UNREPAIRABLE -> READY
QA -pass-> READY -> (PICKED_UP | SHIPPED -> DELIVERED) -> CLOSED
QA -fail-> IN_REPAIR
READY -(1 month past promised date)-> NOTICE_SENT -(2 months)-> ABANDONED
any pre-IN_REPAIR state -> CANCELLED
CLOSED -(same issue within warranty)-> new ticket WARRANTY_CLAIM (no charge)
```

**Business rules**
1. CHECKED_IN must emit the Art. 18 receipt: reception date, owner name, device identification, service class, price and return date; if price/date are unknown, the system must later send them for express acceptance (Ley 1480 art. 18).
2. Quote approval is an explicit recorded act (who, when, channel). Approved quotes are immutable; changes create a new version (RepairShopr; Apple's terms also forbid charging beyond the estimate without consent).
3. Quote timeout: configurable (RepairShopr "auto-expire after X days", "decline older than 30 days"); on expiry send reminder, then treat as DECLINED.
4. Diagnostic fee: waived when the quote is approved, charged when declined (Tecno Tiendas practice; Tokyo Game charges $0 for PS5/Xbox/Switch but COP 30,000 for PS4).
5. Parts: reserve on APPROVED; if stock is 0 create a purchase order and enter WAITING_PARTS; notify the customer on arrival (RepairShopr "part arrival notifications"); consume on QA pass.
6. Warranty repairs (device under sale warranty): must be finished within 30 business days, 60 if a loaner was given (Decreto 735/2013 art. 8). Track a separate SLA clock for these.
7. Repair warranty defaults to 3 months from delivery unless the customer signed a waiver (art. 8). A WarrantyClaim ticket is opened at no charge only if `sameIssue` is confirmed by diagnosis.
8. Abandonment: one month after the agreed return date, send a verifiable notice (email/certified mail/SMS); if not collected within two more months the device is abandoned by law, storage costs accrue to the customer, the shop may not keep it (report to ICBF, verify D.6).
9. Stale-ticket alerts (RepairShopr: "if a ticket isn't updated in 4 hours, notify"); Fixably queues show technician, time in service.
10. Every transition writes StatusHistory and fires a template notification; the public tracking page shows status by ticket number + surname (RepairDesk "Repair Tracker").
11. Data: intake stores credentials encrypted, records the data-loss disclaimer and account sign-out advice (Apple: backup is the customer's responsibility).

**Appointments & logistics.** Drop-off slots (Tecno Tiendas uses Calendly); home pickup priced separately (Bogotá "desde $10.000, ~$20.000"); mail-in: form with photos → deposit → inbound label or workshop address + packaging instructions (original box/padding, remove discs/SD/accessories) → inspection → quote → invoice with pay link before return shipping → tracked return (RepairDesk, OG Repairs). Express tier = shorter promised date at surcharge (Tecno Tiendas "servicio express"; same-day maintenance 2–4 h).

## B. Retail domain model additions

- **Product** (type [PHYSICAL/DIGITAL/PART/SERVICE], categoryId, attributes JSON, condition, warrantyMonths, price incl. IVA). Repair services are SKUs too (Videogamer Shop sells "Reparación de consolas" as a product).
- **Condition** enum: NEW, OPEN_BOX, REFURB_A ("no wear visible at 30 cm"), REFURB_B ("minimal marks at 30 cm"), REFURB_C (visible scratches/dents), USED_AS_IS. All refurb grades must be 100% functional; grades describe appearance only (Refurbito/Refurbed; Back Market: Premium = pristine + genuine parts + battery ≥90%, Excellent battery ≥80%, Good = micro-scratches invisible >20 in, Fair = visible use).
- **ConditionReport** (productUnitId, cosmeticGrade, functionalTests[] (power, HDMI 4K output, disc read, all ports, Wi-Fi/BT, each stick/trigger, battery cycle), batteryHealth%, accessoriesIncluded[], dataWiped bool, accountSignedOut bool, banStatusChecked bool, serialPhoto, sourceTradeInId). Consoles have no public "IMEI blacklist"; verify OEM console-ban status by signing in and keep the seller's ID + invoice (verify).
- **TradeIn** (customerId, deviceId, quotedValue, payout [STORE_CREDIT/CASH], creditBonus%, status [QUOTED→RECEIVED→INSPECTED→ACCEPTED/REJECTED→PAID→LISTED], idScan). GameStop pays "cash or trade credit"; 70% of credit historically went to new games; pre-owned carries ~2× the margin of new. CeX pays cash or voucher.
- **StoreCreditLedger** (customerId, delta, reason, refId).
- **DigitalKey** (productId, codeEncrypted, platform, region, currency, status [AVAILABLE/RESERVED/DELIVERED/REVOKED], orderId, deliveredAt, fraudHoldUntil). Rules: region must match the buyer's account region (platform gift cards are region-bound, verify per platform); reveal only after payment capture and fraud review; log delivery as dispute evidence.
- **PreOrder** (productId, releaseDate, depositAmount, allocation, bonusItems); Phantom (PE) runs "Preventa" for Switch 2/GTA VI; GameStop uses exclusive pre-order bonuses.
- **Bundle** (components[], bundlePrice). **Compatibility** (partId, fitsModelNumbers[], notes).

**Compatibility matrix (examples)**

| Part | Fits | Does not fit |
|---|---|---|
| PS5 PSU ADP-400DR | CFI-10xxA/B (iFixit) | CFI-11xx/12xx (different PSU, verify part no.) |
| PS5 heatsink CFI-1215 | CFI-12xx only (Fasttech) | 10xx/11xx (larger heatsink, liquid metal) |
| PS5 disc drive | Slim CFI-20xx drive is detachable, sold separately | Pro CFI-70xx ships without drive |
| Xbox Series X board/cooling | 2024 refresh (Digital white, 2TB Galaxy Black): new motherboard, heat pipes | Original model 1882 (vapor chamber) |
| Switch screen | HEG-001 OLED 7" ≠ HAC-001/(-01) LCD ≠ HDH-001 Lite 5.5" | - |
| Joy-Con HAC-015/016 | All Switch, OLED | Lite (integrated sticks) |

**Category tree**: Consoles › PlayStation (PS5 Standard/Digital/Slim/Pro, PS4) · Xbox (Series X/S, One) · Nintendo (Switch, Lite, OLED, Switch 2) · Retro · Handheld PC → each × condition · **Controllers & input** (DualSense/Edge, Xbox Wireless, Joy-Con, Pro Controller, arcade sticks, wheels) · **Accessories** (headsets, charging docks, SSD/storage, cables, cases, stands) · **Games** (physical by platform; used; pre-orders) · **Digital** (PSN/Xbox/eShop/Steam gift cards; PS Plus/Game Pass/NSO; keys) · **Spare parts** (per platform: HDMI ports, PSUs, disc drives, fans/heatsinks, thermal paste/liquid metal, stick modules (potentiometer/Hall/TMR), batteries, USB-C ports, shells/buttons, cap kits; iFixit store categories: batteries, joysticks, buttons, thermal, cases, screens, motherboards, optical drives, tool kits) · **Repair services** (SKUs) · **Collectibles/merch** (Phantom/Lawgamers: Funko, TCG, chairs).

**Attribute sets**: Console: platform, family, modelNumber, region, storage, edition, condition, includes[], warrantyMonths. Controller: model revision (BDM-0x0), stickType, color. Game: platform, region (NTSC-U/PAL/NTSC-J), edition, media (disc/cartridge), condition (disc scratches, case, manual). Digital: platform, region, currency/denomination, deliveryType. Part: partNumber, OEM/aftermarket, fitsModels[].

## C. Operational playbook

**Intake checklist**: model number + serial (photo), accessories received (controllers, cables, discs, SD), cosmetic damage map, powers on / video out, liquid or prior-repair evidence, customer-reported issue, credentials (encrypted), data-loss and account sign-out disclaimer, warranty status (sale warranty?), promised date & price or "to be quoted", signed terms, receipt printed/sent (art. 18).

**QA checklist**: boots; HDMI at 1080p and 4K (OG Repairs tests multiple resolutions); disc read/eject; all USB/LAN ports; Wi-Fi/BT pairing; fan noise/temps after 15 min load; every stick centre/dead-zone and trigger; battery charge/discharge; firmware boots to dashboard; original screws/covers back; serial matches ticket; photos after; data intact ("sin borrar datos" is a selling point in Medellín).

**Warranty policy template**: 3 months on part + labor from delivery (legal floor unless written waiver); covers the same fault only, not new faults or physical/liquid damage (mconsolas wording); free re-diagnosis inside warranty (Tecno Tiendas); voided by third-party opening; exclusions listed on the receipt; international benchmarks: 30 days (Tokyo Game), 90 days (Apple), 12 months (OG Repairs UK).

**KPIs**: First-time-fix rate = tickets closed without warranty claim ÷ tickets (median 75%, top 86%); repeat-repair rate = WarrantyClaims ÷ closed; resolution time = deliveredAt − receivedAt (median 5 d, top 3 d); technician utilization = billable ÷ available hours (75–85%; above mid-80s kills same-day slack); revenue per ticket; quote approval rate = APPROVED ÷ QUOTED; abandoned rate; ARPBH (revenue ÷ billable hours).

**Pricing benchmarks** (Tokyo Game Medellín, Sept 2026 pages, COP unless noted; verify locally)

| Repair | COP | Ref. abroad | Parts source | Time |
|---|---|---|---|---|
| Diagnostic | $0 (PS5/Xbox/Switch), $30,000 (PS4) | - | - | 1–2 h |
| PS5 HDMI port (+ cleaning) | 390,000 | €65 (ES), ~COP 321k (UK OG) | HDMI 2.1 port: AliExpress/iFixit | 6–12 h |
| PS5 maintenance / liquid metal | 180,000 | from €50 | thermal paste, liquid metal | 24 h |
| PS5 PSU (Fat) | 520,000 | ADP-400DR US$79.99 (iFixit) | iFixit / AliExpress | 3–8 h |
| PS5 disc drive | 550,000 | - | - | 24–48 h |
| PS5 APU reballing | 590,000 | - | - | 3–5 bus. days |
| PS4 HDMI / PSU / drive / maint. | 200,000 / 290,000 / 250,000 / 90,000 | - | - | 24–48 h |
| Xbox Series X HDMI / maint. | 390,000 / 160–180,000 | - | - | 6–12 h / 2–4 h |
| Stick module (DualSense/DS4/Xbox) | 45,000 std, 70,000 magnetic (Hall) | from €20 | AliExpress Hall/TMR modules | 40 min–24 h |
| Joy-Con stick | 80,000 | - | - | 40 min |
| Switch USB-C / M92T36, screen | "cotizar" (no public price) | - | - | 6–12 h |
| Retro recap | no CO price found | cap kits US$4–26 (Console5) + labor | Console5 | verify |
| Home pickup Bogotá | from 10,000 (~20,000) | - | - | - |

Shops also sell "chip 128 GB + 17 juegos" (COP 320,000–390,000); exclude modding from the system: copyright risk (verify).

## D. Legal checklist (Colombia)

1. Repair-service warranty: 3 months from delivery unless the waiver is informed and accepted in writing (Ley 1480 art. 8, Consumoteca). verify.
2. Used/refurbished goods: 3 months unless written agreement; may be sold without warranty only with written acceptance (art. 8). verify.
3. New goods: 1 year default when no term is stated (art. 8).
4. Intake receipt: date, owner, device ID, service class, price, return date; unknown price/date must be sent later for express acceptance (art. 18; Consumoteca, MinCIT). verify.
5. Custody and conservation duty for the device and its parts (art. 18).
6. Abandonment: notice after 1 month past return date, 2 more months, then abandoned; customer bears storage; shop cannot appropriate; disposal per Decreto 1413/2018 → ICBF (PDAbogados; decree text not fetched). verify.
7. Warranty repairs: 30 business days, 60 with loaner (Decreto 735/2013 art. 8); claim answer 15 business days (search summary only). verify.
8. Warranty remedies: free repair incl. transport and parts; replacement/refund if unrepairable; repeated failure → consumer chooses; parts availability after warranty (art. 11).
9. Information: clear, in Spanish, price in COP with taxes (arts. 23–26).
10. Online sales: retracto 5 business days (exceptions: services already started, relevant to redeemed keys, verify); seller identity, order summary, payment reversal (arts. 47, 50–51).
11. Invoicing: factura electrónica for all obligated; content incl. CUFE, consumidor final NIT 222222222222; Res. 000165/2023 and 000227/2025 (Gerencie); POS document capped at 5 UVT per sale; source not reached. verify.
12. Data on devices: prior informed authorization (art. 9), security/confidentiality (art. 4), duties (art. 17), treatment policy (art. 25 per mirror), Ley 1581/2012. verify.

## E. Market references

| Store | Country | URL | Notable |
|---|---|---|---|
| Tecno Tiendas | CO | tecnotiendas.com.co | Free diag if approved; 3-month warranty; 24–72 h; Calendly booking; home pickup; WhatsApp |
| Tokyo Game | CO | tokyogame.com.co | Public price/time tables per console; $0 diag; 30-day warranty; "sin borrar datos"; national shipping |
| Tecnology Tech | CO | tecnology-tech.com.co | Written warranty; microsoldering (M92T36, HDMI 2.1, liquid metal); pickup/delivery |
| Tecnilogy Games | CO | tecnilogy.com | WhatsApp pre-filled requests; OS recovery, storage upgrades |
| Mantenimiento de Consolas | CO | mconsolas.netlify.app | Home-only; 90-day warranty wording; Nequi/Daviplata/PSE |
| Videogamer Shop | CO | videogamershop.com.co | Sells repair as a shop product (site unreachable, verify) |
| Phantom | PE | phantom.pe | Preventa, cuotas sin intereses, WhatsApp, categories incl. collectibles |
| Lawgamers | PE | lawgamers.com | Gift Card category, Garantía + return policy pages |
| Addi | CO | co.addi.com | BNPL: cédula + WhatsApp, 3×0%, up to 24 cuotas |
| Back Market | FR/US | backmarket.com | Grades Fair/Good/Excellent/Premium, 100% functional, NAD rate |
| CeX | UK | webuy.com | Cash vs voucher trade-in; long used-goods warranty (verify) |
| GameStop | US | gamestop.com | Trade credit/cash, pre-owned margins, pre-order bonuses, Pro loyalty |
| iFixit | US | ifixit.com | Fits-model part data, repairability scores, guides |
| OG Repairs | UK | ogrepairs.com | Mail-in with QR label, 12-month warranty, 4K test |
| RepairShopr/RepairDesk/Fixably | US/FI | see G | Ticket/estimate/portal/mail-in reference software |

## F. Glossary

- **RepairTicket**: one repair job on one device. **Intake/Check-in**: reception with receipt and photos. **Outtake**: delivery form with work summary and signature. **Diagnostic fee**: charge for diagnosis, usually waived on approval. **Quote/Estimate**: parts + labor offer needing approval. **Backorder**: part not in stock, on order. **QA**: post-repair test checklist. **FTFR**: share fixed without return. **Repeat-repair rate**: warranty claims ÷ closed tickets. **Resolution time**: receipt to delivery. **Utilization**: billable ÷ available hours. **ARPBH**: revenue per billable hour. **SLA**: promised time per tier. **Loaner**: device lent during repair (extends legal term to 60 days). **Express tier**: surcharge for faster promise. **Mail-in**: repair by courier with label. **Reballing**: re-soldering BGA chip balls. **Liquid metal**: PS5 APU thermal interface. **HDMI 2.1 port**: PS5/Series X video connector. **M92T36**: Switch USB-C power IC. **BLOD**: PS4 blue light of death. **Hall effect / TMR stick**: magnetic sticks immune to drift. **Potentiometer stick**: standard drift-prone stick. **Joy-Con drift**: stick self-moving. **Recapping / cap kit**: replacing aged capacitors. **Chip/RGH**: modding; excluded. **Open box / Grade A-B-C**: cosmetic tiers. **NAD rate**: "not as described" disputes. **Trade-in**: buying used stock for credit/cash. **Store credit**: ledger balance. **Pre-order**: sale before release. **Bundle**: packaged products. **Digital key / gift card**: codes, region-bound. **Region lock**: platform/country restriction. **3DS/AVS/auth-and-capture**: card checks; capture within 7 days. **CUFE**: electronic invoice hash. **Documento equivalente POS**: small-sale receipt. **UVT**: tax unit. **Ley 1480**: Consumer Statute. **Decreto 735/2013**: warranty procedure. **Decreto 1413/2018**: abandoned goods. **Bienes mostrencos/ICBF**: unclaimed property. **Retracto**: 5-day withdrawal. **Reversión del pago**: chargeback right. **Ley 1581**: habeas data. **Addi/Sistecrédito**: Colombian BNPL. **PSE/Nequi/Daviplata**: local payments. **ShipStation**: label integration. **Calendly**: slot booking. **CFI-/HAC-/HEG-/HDH-/BDM-/ADP-**: Sony/Nintendo/controller/PSU model prefixes.

## G. Sources

RepairShopr tickets/estimates: https://repair.uservoice.com/knowledgebase/articles/1851781-tickets · https://repair.uservoice.com/knowledgebase/articles/1851796-estimates · RepairDesk: https://www.repairdesk.co/features/repair-ticket-management-software/ · https://www.repairdesk.co/mail-in-repair/ · Fixably: https://www.fixably.com/features/repair-order-management · KPIs: https://vsight.io/field-service-kpis/ · https://financialmodelslab.com/blogs/kpi-metrics/computer-repair · Ley 1480: https://www.mincit.gov.co/ministerio/normograma-sig/procesos-misionales/facilitacion-del-comercio-y-defensa-comercial/leyes/ley-1480-de-2011.aspx · https://consumoteca.com.co/articulo-8-de-la-ley-1480-estatuto-del-consumidor · https://consumoteca.com.co/articulo-11-de-la-ley-1480-estatuto-del-consumidor · https://consumoteca.com.co/articulo-18-de-la-ley-1480-estatuto-del-consumidor · Decreto 735/2013: https://www.alcaldiabogota.gov.co/sisjur/normas/Norma1.jsp?i=52670 · Abandono: https://pdabogados.com/abandono-de-bienes-dejados-para-reparacion-por-garantia/ · https://www.mundovideo.com.co/seccion-juridica/equipos-dejados-en-reparacion-pueden-generar-gastos-de-conservacion-por-abandono/ · Ley 1581: https://www.alcaldiabogota.gov.co/sisjur/normas/Norma1.jsp?i=49981 · Factura electrónica: https://www.gerencie.com/factura-electronica.html · Colombian shops: https://tecnotiendas.com.co/servicio-tecnico-playstation/ · https://tecnotiendas.com.co/servicio-tecnico/ · https://tokyogame.com.co/servicio-tecnico-reparacion-ps5-medellin/ · https://tokyogame.com.co/servicio-tecnico-reparacion-ps4-medellin/ · https://tokyogame.com.co/servicio-tecnico-reparacion-xbox-medellin/ · https://tokyogame.com.co/servicio-tecnico-reparacion-nintendo-switch-medellin/ · https://www.tecnology-tech.com.co/ · https://www.tecnilogy.com · https://mconsolas.netlify.app/ · https://www.computadoresenbogota.com/market/producto/servicio-tecnico-para-control-play-5/ · Abroad: https://repararmandops5.es/reparacion-hdmi-ps5/ · https://ogrepairs.com/products/playstation-5-ps5-hdmi-port-repair-service · https://www.apple.com/legal/sales-support/terms/repair/generalservice/servicetermsen/ · Hardware: https://en.wikipedia.org/wiki/PlayStation_5 · https://www.ifixit.com/Device/PlayStation_5 · https://www.ifixit.com/products/playstation-5-power-supply · https://fasttechstore.com/playstation-5-1/p/playstation-5-heat-sink-cfi-1215 · https://en.wikipedia.org/wiki/Xbox_Series_X_and_Series_S · https://en.wikipedia.org/wiki/Nintendo_Switch · https://www.ifixit.com/Device/Nintendo_Switch · https://en.wikipedia.org/wiki/DualSense · https://www.ifixit.com/Store/Game-Console · https://console5.com/store/ · Retail: https://refurbito.com/en/posts/refurbished-condition-grades-explained · https://www.sellermania.com/en/blog/refurbished/back-market-seller-grades-marketplaces/ · https://en.wikipedia.org/wiki/GameStop · https://en.wikipedia.org/wiki/CeX_(company) · https://www.phantom.pe/ · https://www.lawgamers.com/ · https://co.addi.com/ · Fraud: https://docs.stripe.com/disputes/prevention/best-practices
