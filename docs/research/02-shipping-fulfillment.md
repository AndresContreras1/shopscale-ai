# Research: shipping, logistics, fulfillment and returns (Colombia-first)

> Research notes, September 2026. Items marked **verify** could only be confirmed on marketing pages, not developer docs.

## A. Recommended decisions

**MVP (months 0–3): one aggregator behind an adapter, not direct carrier contracts.**
1. **First: Envia.com**: public REST docs with sandbox (`api-test.envia.com`) and prod (`api.envia.com`), plus Queries API (carrier availability, address validation) and Geocodes API; covers rates, labels, tracking, pickups, cancel, manifests, commercial invoices. Widest Colombian carrier list found: Coordinadora, Servientrega, InterRapidísimo, TCC, Envía, Mensajeros Urbanos, 99 Minutos, Cabify, plus FedEx/DHL Express (international path later, same adapter). COD offered with funds in ~72 h; you can upload your own negotiated rates. Verify: COD fields in the API and per-label fees for CO.
2. **Second (parallel or fallback): Aveonline**: Colombian, public endpoint docs (`integraciones.aveonline.co`): JWT (1 h), quote (`tipo: cotizar2`) returning per-carrier `fletetotal` + `diasentrega`, guide creation returning `numguia`, PDF (`rutaguia`) and base64 thermal label (`archivorotulo`), status endpoint, COD (`contraentrega`, `valorrecaudo`), returns activation, webhooks (claimed on marketing page, verify). Uses DANE codes. Cancellation/pickup endpoints not documented. Mipaquete is an equivalent alternative (Postman v2 docs, sandbox, auto pickup request, WhatsApp notifications, COD; carriers Servientrega/Envía/Coordinadora/TCC).

**Growth (month 6+):** direct **Coordinadora** contract once volume justifies negotiated rates: documented SOAP WS (quote, guide, tracking with images, pickups, recaudos; payment mode 4 = COD; 8-digit DANE codes) and a new OpenID/API-key developer portal (`developers.coordinadora.com`, login-gated); free pickups; COD in 1,600+ towns with daily/weekly/biweekly settlement. Add **Interrapidísimo** via aggregator for rural reach (1,104 municipalities, "Pago en Casa" COD, Sunday delivery +COP 5,000) and **Mensajeros Urbanos** (OAuth2 API, webhooks; Bogotá/Cali/Medellín/Barranquilla/Villavicencio) for same-day. If you outsource storage: **Melonn** or **Cubbo** (both have REST + webhooks). International: **DHL Express MyDHL API** direct (Rating, Landed Cost, Shipment with customs docs, Pickup, Tracking). Skip Shipit (docs are Chile-only) and 4-72/Deprisa as primary (no public API; 4-72 is import-oriented; Deprisa is express/air, 80 kg max).

## B. Comparison

| Provider | Public API | Quote | Label | Tracking webhooks | COD | Coverage | Docs |
|---|---|---|---|---|---|---|---|
| Servientrega | Partial: public REST cotizador; corporate "Servicli" API (guías, anulación, rastreo, recolección, recaudo) after onboarding | Yes | Corporate API | Not evidenced (verify) | Yes (pago contraentrega, CIFIN check) | CO + EC/PE/PA/US | mobile.servientrega.com/ApiIngresoCLientes/Help |
| Coordinadora | Yes (SOAP WS; new OpenID portal) | Yes (nat./intl.) | Yes | No (poll `Seguimiento_*`; portal may add, verify) | Yes (mode 4) | 1,600+ towns; free pickups | sandbox.coordinadora.com/ags/1.4/server.php?doc |
| Interrapidísimo | Not public (via aggregators) | via aggr. | via aggr. | via aggr. | Yes ("Pago en Casa") | 1,104 municipios; 3,250+ points | inter-rapidisimo.com |
| TCC | Shopify app (2024); WS via account (verify) | via aggr. | App/aggr. | No evidence | verify | National | apps.shopify.com/tcc-1 |
| Envía (Colvanes) | WS (community PHP client): liquidación, guía, tracking; sandbox | Yes | Yes | No | verify | National | github.com/saulmoralespa/envia-colvanes-api-php |
| Deprisa | No public docs | - | - | - | not found | 52 air destinations, ≤80 kg | deprisa.com |
| 4-72 | No public docs | - | - | UPU track | not found | Postal; intl 192 dest. | 4-72.com.co |
| **Envia.com** | Yes REST, sandbox | Yes | PDF | Yes (verify CO) | Yes, ~72 h settlement | 12 CO carriers + DHL/FedEx | docs.envia.com |
| **Aveonline** | Yes REST | Yes, multi-carrier | PDF + thermal b64 | Claimed | Yes | 5+ carriers (Envía, TCC, Saferbo…) | integraciones.aveonline.co/docs |
| Mipaquete | Yes (Postman v2), sandbox | Yes | Yes | Not documented | Yes | Servientrega/Envía/Coordinadora/TCC | documenter.getpostman.com/view/14212363/Tzm8GFfQ |
| Envíoclick | Yes, login-gated docs | Yes | Yes | Yes | Yes | MX+CO, 30+ carriers | envioclick.com/co |
| Envíame | Yes (v2/v3 + webhooks) | Yes | Yes | Yes | Yes (carrier fee deducted) | Servientrega, Coordinadora, Inter, MU, TCC, Deprisa, 4-72, FedEx | docs.enviame.io |
| Shipit | Yes | Yes | Yes | Yes | ? | Chile only in docs | developers.shipit.cl |
| Cubbo (3PL) | Yes: `api.cubbo.com/v1`, OAuth client creds | n/a | 3PL ships | Yes | verify | MX/BR/CO | developers.cubbo.com |
| Melonn (3PL) | Yes REST (creds on request) | n/a | 3PL ships | Yes | verify | CO/MX, same/next-day | melonn.com/integraciones/melonn-api |
| Mensajeros Urbanos | Yes, OAuth2 | `/delivery/calculation` | n/a (courier) | Yes (POST to partner URL) | Cash/datáfono/online | 5 cities | apis.mensajerosurbanos.com/mensajeria |
| Pibox / Liftit | API exists (help centers; Liftit also SFTP/CSV) | verify | verify | verify | verify | Urban / truck B2B | support.pibox.com, help.liftit.co |

## C. Domain design

**Order state machine** (extend current): `PENDING_PAYMENT → PAID | EXPIRED | CANCELLED`; `PAID → COD_CONFIRMATION_PENDING` (COD orders: WhatsApp/phone confirm before allocation) `→ PROCESSING` (stock allocated, pick list) `→ PARTIALLY_SHIPPED | SHIPPED → DELIVERED → COMPLETED` (auto after retracto window: 5 business days post-delivery); `DELIVERED → RETURN_OPEN → REFUNDED | EXCHANGED`; `PROCESSING → BACKORDERED` (pre-orders/backorders hold a reservation with ETA). Order fulfillment status is derived from its shipments.

**Shipment state machine**: `DRAFT → QUOTED → LABEL_CREATED → PICKUP_SCHEDULED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED`; `OUT_FOR_DELIVERY → DELIVERY_FAILED → (retry ≤2) | RETURNED_TO_SENDER`; `IN_TRANSIT → EXCEPTION (lost/damaged) → CLAIM_OPEN → CLAIM_SETTLED`; `LABEL_CREATED|PICKUP_SCHEDULED → CANCELLED`. Carrier raw codes map to these via per-adapter tables; keep the raw event too.

**Return (RMA) state machine**: `REQUESTED → APPROVED | REJECTED`; `APPROVED → LABEL_ISSUED` (warranty: seller pays; retracto: customer ships at own cost, or offer prepaid label deducted) `→ IN_TRANSIT → RECEIVED → INSPECTING → ACCEPTED | DISPUTED`; `ACCEPTED → REFUND_PENDING → REFUNDED | EXCHANGE_SHIPPED`; disposition enum `RESTOCK | REFURBISH | SCRAP | RETURN_TO_VENDOR`. Reason enum drives the legal path: `RETRACTO` (5 business days, refund ≤30 calendar days, no deductions), `WARRANTY` (Ley 1480 art. 8: 1 year new goods; Decreto 735: written intake receipt, reasoned answer, repair ≤30 business days, transport paid by seller, repeat failure → customer picks refund or replacement), `DOA`, `WRONG_ITEM`, `REVERSION_PAGO` (art. 51: fraud/non-delivery/mismatch, claim within 5 business days).

**Repair order**: `REQUESTED → INBOUND_LABEL_ISSUED | DROP_OFF_SCHEDULED → RECEIVED (intake: serial, accessories checklist, ≥6 photos, cosmetic grade, power-on test, tamper seal) → DIAGNOSING → QUOTE_SENT → APPROVED | DECLINED → REPAIRING → QC → READY → SHIPPED_BACK | PICKED_UP → CLOSED`. Warranty repairs carry the 30-business-day clock from RECEIVED; repair work gets an implied 3-month guarantee unless disclaimed in writing (art. 8). Chain of custody = append-only `custody_event` (who, where, when, photo hash) per device.

**Shipment entity**: `id, orderId|rmaId|repairId, direction (OUTBOUND|RETURN|REPAIR_IN|REPAIR_OUT), carrierCode, serviceCode, providerRef (aggregator id), trackingNumber (guía), labelUrl, labelBase64, labelFormat (PDF|THERMAL|ZPL), status, rawStatus, packages[{weightKg, lengthCm, widthCm, heightCm, volumetricKg, declaredValueCop, contents, serials[]}], origin, destination, cod{enabled, amountToCollect, feeCop, settledAt}, insuredValueCop, quotedCost, actualCost, currency, promise{minDays, maxDays, promisedDate}, pickup{scheduledAt, confirmationId}, attempts, pod{receiverName, docId, signatureUrl, photoUrl}, events[], idempotencyKey, createdAt`.

**Carrier adapter (pluggable)**: Spring beans keyed by `CarrierCode`, selected by a `CarrierRouter` (rules: COD?, rural?, weight, price, promise):

```java
public interface CarrierGateway {
  CarrierCode code();
  Set<Capability> capabilities();          // COD, PICKUP, WEBHOOK, CANCEL, INTERNATIONAL, THERMAL_LABEL
  List<RateQuote> quote(RateRequest r);     // normalized: price, currency, minDays, maxDays, serviceCode
  ShipmentResult createShipment(ShipmentRequest r);
  LabelDocument label(String providerRef, LabelFormat f);
  TrackingSnapshot track(String trackingNumber);
  PickupConfirmation schedulePickup(PickupRequest r);
  void cancel(String providerRef);
  List<TrackingEvent> parseWebhook(byte[] body, Map<String,String> headers); // verify signature, normalize
}
```

Add a polling job for carriers without webhooks, an outbox/idempotent event handler (Redis dedupe on `providerRef+eventId`), and a `RateCache` (Redis, TTL ~1 h keyed by origin/destination/dims).

**Address model (CO + international)**: `countryIso2; adminArea{name, code}` (departamento, DIVIPOLA 2-digit); `locality{name, code5, code8}` (municipio; `cod_mpio` 5-digit = 2 dept + 3 muni; Coordinadora wants 8 digits, e.g. `05001000`); `subLocality` (localidad/comuna/barrio); structured street: `viaType (CL|KR|DG|TV|AV|AC|AK|CIR), viaNumber, viaLetter, bis, viaQuadrant (SUR|ESTE|NORTE|OESTE), crossNumber, crossLetter, plate, complement` (torre/apto/interior/casa/local); `formattedLine` ("Cra 7 # 12-34 Apto 501"); `rural{isRural, vereda, corregimiento, landmark}` (veredas have no nomenclature: require landmark + GPS + phone); `geo{lat,lng}`; `postalCode` (CO 6-digit, optional; required abroad); `contact{name, docId, phoneE164, whatsappOptIn}`. International: generic `line1/line2, city, stateCode, postalCode`. Seed municipalities from DIVIPOLA dataset `gdxc-w37w` (JSON fields `cod_dpto, dpto, cod_mpio, nom_mpio, tipo_municipio, longitud, latitud`; note comma decimals); validate addresses with Envia Geocodes/Queries; DIAN's `Nomenclatura_2012.pdf` is the abbreviation list.

**Checkout flow**: quote by (origin warehouse, destination `cod_mpio`, sum of package dims/weight, declared value) → show 2–3 options (cheapest / fastest / pickup point / in-store pickup free) → promise = carrier `diasentrega` + 1 business-day handling buffer → on PAID create guide, schedule pickup, email/WhatsApp tracking link; tracking page reads normalized events; failed delivery triggers customer contact + retry; two failures → return to sender + refund minus outbound cost (retracto rules don't apply to failed delivery, verify policy wording with SIC guidance).

**COD mitigations**: eligibility (city coverage, order value cap e.g. ≤COP 1.5 M, no first-order consoles over cap), mandatory WhatsApp confirmation before allocation, deposit/anticipo for high-ticket, blocklist by phone/doc after rejection, carrier fee (3–5 % + minimum: blog figures, verify contract), automated reconciliation of `Recaudos_consultar`/settlement files vs. `cod.settledAt`.

## D. Operational checklist

- **Packaging**: consoles in original box inside a rigid new corrugated outer box (Coordinadora FAQ: electronics in original packaging, new rigid boxes, no rope); ~5 cm cushioning all sides, void fill, H-taping, "frágil" label; controllers with Li-ion batteries: check each carrier's battery rules (verify); photograph sealed package + serials; tamper-evident seal for repairs.
- **Insurance**: declare full retail value (carrier liability is "up to declared value" per Coordinadora); budget the declared-value handling fee (% not public, verify per contract); claims by email with photos within carrier window.
- **Warehouse**: bins `ZONE-AISLE-SHELF-BIN`, SKU + serial barcodes, scan at receive/pick/pack/ship; packing slip with order id QR; multi-warehouse readiness = `warehouseId` on stock + shipment; split shipments only when backordered item ETA > 3 days.
- **SLAs**: ship same business day if paid before 14:00; RMA approval ≤1 business day; repair diagnosis ≤3 business days; warranty repair ≤30 business days (legal).
- **KPIs** (ShipBob definitions): on-time ship rate ≥98 %, picking accuracy ≥99.5 %, order cycle time, inventory accuracy ≥99 %, damage rate <0.5 %, COD rejection rate <10 %, return rate, cost per order, failed-delivery rate, claim resolution days.

## E. Glossary

- **Aggregator**: one API in front of many carriers (Envia.com, Aveonline, Mipaquete, Envíoclick, Envíame).
- **3PL**: outsourced warehouse + fulfillment (Melonn, Cubbo).
- **Guía**: Colombian term for waybill/label and tracking number.
- **Pago contraentrega / recaudo / Pago en Casa**: cash on delivery collected by the carrier.
- **Rótulo / sticker térmico**: thermal label format (Aveonline `archivorotulo`).
- **DANE / DIVIPOLA**: national statistics office and its 2+3(+3)-digit territorial codes.
- **Nomenclatura vial**: CL/KR/DG/TV/AV/AC/AK street grid; "Calle 42 # 15-34" = on Calle 42, 34 m from Carrera 15.
- **Vereda / corregimiento**: rural subdivisions without street nomenclature.
- **Código postal**: 6-digit postal code managed by 4-72 (verify format).
- **Ley 1480 de 2011**: Consumer Statute; art. 8 warranty term, art. 47 retracto, art. 51 payment reversal.
- **Retracto**: 5-business-day withdrawal right for distance sales; refund ≤30 calendar days.
- **Garantía legal**: 1 year for new goods; Decreto 735/2013 sets the 30-business-day repair term.
- **Reversión del pago**: card reversal for fraud/non-delivery/mismatch (art. 51).
- **SIC**: Superintendencia de Industria y Comercio, consumer regulator.
- **CIFIN**: credit bureau check used in Servientrega's TuRecaudo onboarding.
- **RMA**: return merchandise authorization workflow.
- **Disposition**: what happens to a returned unit (restock, refurbish, scrap, RTV).
- **Chain of custody**: auditable handoff log for a customer's device.
- **POD**: proof of delivery (name, signature, photo).
- **Backorder / pre-order**: order accepted without stock, with ETA.
- **Volumetric weight**: L×W×H/divisor; carriers charge the greater of real vs. volumetric.
- **Valor declarado**: declared value; caps carrier liability and drives insurance fee.
- **Pickup point / punto de servicio**: carrier office where customers collect parcels.
- **Webhook vs polling**: carrier pushes events vs. you query tracking on a schedule.
- **Outbox / idempotency key**: patterns to process carrier events exactly once.
- **Adapter / Strategy / Router**: pluggable carrier integration pattern.
- **Karrio**: open-source self-hosted multi-carrier API (Python/Django, Docker); reference model for the adapter.
- **MyDHL API**: DHL Express REST (Basic auth; test `/mydhlapi/test`, 500 calls/day).
- **FedEx REST APIs / UPS APIs**: OAuth 2.0; Rate & Transit (with duty/tax estimate), Ship, Track (≤30 numbers/request at FedEx).
- **HS code 9504.50**: video game consoles and machines (parts/accessories inclusion: verify in Colombia's arancel).
- **Incoterms DAP / DDP**: buyer pays duties on arrival vs. seller prepays all (DDP = landed cost at checkout).
- **Landed cost**: price + freight + insurance + duties + taxes.
- **De minimis**: duty-free import threshold; being removed/suspended (US 2025, EU 2026, verify).
- **Commercial invoice / customs docs**: generated by DHL/Envia.com shipment calls.
- **KPIs**: on-time ship rate, picking accuracy, order cycle time, inventory accuracy, damage rate, return rate, cost per order.

## F. Sources

- Servientrega API help: https://mobile.servientrega.com/ApiIngresoCLientes/Help ; corporate API doc: https://www.scribd.com/document/702533525/Documentacion-API-3 ; e-commerce: https://www.servientrega.com/wps/portal/soluciones-ecommerce ; WooCommerce plugin: https://github.com/saulmoralespa/shipping-servientrega-wc
- Coordinadora WS doc: http://sandbox.coordinadora.com/ags/1.4/server.php?doc ; PHP client: https://github.com/saulmoralespa/coordinadora-webservice-php ; COD: https://coordinadora.com/servicios/pago-contra-entrega/ ; FAQ: https://coordinadora.com/preguntas-frecuentes/ ; Suite: https://coordinadora.com/suite-logistica/ ; dev portal (login): https://developers.coordinadora.com/
- Interrapidísimo: https://www.interrapidisimo.com/ ; TCC Shopify app: https://apps.shopify.com/tcc-1 ; Envía Colvanes client: https://github.com/saulmoralespa/envia-colvanes-api-php ; Deprisa: https://deprisa.com/es/servicios/empresas ; 4-72: https://www.4-72.com.co/publicaciones/261/e-commerce/
- Envia.com: https://docs.envia.com/ , https://envia.com/es-CO , https://envia.com/es-CO/transportadoras
- Aveonline: https://integraciones.aveonline.co/docs/nacional/cotizacion/ , https://integraciones.aveonline.co/docs/nacional/generacionGuia/ , https://aveonline.co/servicios/integraciones/
- Mipaquete: https://www.mipaquete.com/conecta-tu-tiendavirtual/api-integracion , https://documenter.getpostman.com/view/14212363/Tzm8GFfQ
- Envíoclick: https://blog.envioclick.com/conecta-envioclick-a-tu-ecommerce-via-api/ , https://blog.envioclick.com/pago-contra-entrega-en-colombia-como-funciona-y-como-escalarlo-sin-perder-control/
- Envíame: https://docs.enviame.io/ , https://enviame.io/contra-entrega-colombia/ ; Shipit: https://developers.shipit.cl/llms.txt
- Cubbo: https://developers.cubbo.com/ , https://context7.com/websites/developers_cubbo ; Melonn: https://www.melonn.com/integraciones/melonn-api/
- Mensajeros Urbanos: https://apis.mensajerosurbanos.com/mensajeria/ , https://polancomensajeros.github.io/ ; Pibox: https://support.pibox.com/en/collections/3680535-pibox-api ; Liftit: https://help.liftit.co/
- Legal: art. 47 https://leyes.co/el_estatuto_del_consumidor/47.htm ; art. 51 https://leyes.co/el_estatuto_del_consumidor/51.htm ; art. 8 https://www.consumoteca.com.co/articulo-8-de-la-ley-1480-estatuto-del-consumidor/ ; Decreto 735/2013 https://www.alcaldiabogota.gov.co/sisjur/normas/Norma1.jsp?i=52670
- DIVIPOLA: https://www.datos.gov.co/Mapas-Nacionales/DIVIPOLA-C-digos-municipios/gdxc-w37w , https://www.datos.gov.co/resource/gdxc-w37w.json , https://www.dane.gov.co/index.php/sistema-estadistico-nacional-sen/normas-y-estandares/nomenclaturas-y-clasificaciones/nomenclaturas/codificacion-de-la-division-politica-administrativa-de-colombia-divipola
- Addresses: https://www.portafolio.co/tendencias/nomenclatura-urbana-como-ubicarse-y-como-leer-las-direcciones-en-colombia-580331 , https://www.catastrobogota.gov.co/recurso/nomenclatura , https://www.dian.gov.co/atencionciudadano/formulariosinstructivos/Formularios/2012/Nomenclatura_2012.pdf
- International: https://developer.dhl.com/api-reference/dhl-express-mydhl-api , https://developer.fedex.com/api/en-us/catalog/rate.html , https://developer.fedex.com/api/en-us/guides/best-practices.html , https://www.ups.com/us/en/business-solutions/expand-your-online-business/upgrade-digital-technology/developer-resource-center , https://www.tariffnumber.com/2025/950450 , https://blog.winsbs.com/2025/12/02/ddp-vs-dap/ , https://landmarkglobal.com/eu/en/news-insights/difference-between-ddp-and-dap/ , https://github.com/karrioapi/karrio
- Ops/KPIs: https://www.shipbob.com/blog/warehouse-kpis/ ; COD stats: https://www.tiendanube.com/blog/empresas-de-envios-en-colombia/ , https://tumensajeroexpress.com.co/pago-contra-entrega-ecommerce-cuando-conviene/
