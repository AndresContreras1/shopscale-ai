# Demo guide (5 minutes)

Start the stack with `docker compose up --build` and open http://localhost:8088
(or run backend and frontend locally, see the README).

## 1. Storefront (1 min)
1. Browse the catalog: search, filter by category, sort. Stock badges come from the inventory service.
2. Add a product to the cart and sign in with **CUSTOMER** (one click on the login page).
3. Checkout: the order is created as `PENDING_PAYMENT` and the units are **reserved** for 15 minutes.
4. Pay (simulated): the reservation becomes a sale and the stock leaves the warehouse.

## 2. Inventory under pressure (1.5 min)
1. Sign in as **ADMIN** and open *Back office > Inventory*.
2. Select a product with few units and run the **flash sale simulator** with 200 buyers.
3. Result: exactly as many buyers as units are served, the rest get "not enough stock",
   `Oversold? NO`. Explain the atomic conditional `UPDATE`.
4. Open the product's **stock ledger**: every reservation and release is recorded.

## 3. Dashboard and AI (1.5 min)
1. *Dashboard*: revenue, orders, average order value, stock alerts, daily revenue chart,
   restock suggestions computed from sales velocity and supplier lead time.
2. *AI reports*: generate the inventory report in English or Spanish. Open "Facts sent to the AI"
   to show that the model only receives computed numbers.
3. Mention the fallback: without an API key or if the provider fails, the feature still answers.

## 4. Scalability and security (1 min)
1. Footer: "Served by API instance ..." changes between requests: Nginx balances two replicas.
2. `docker compose up -d --scale api=4` adds capacity without code changes.
3. *Products > Import CSV* with a generated feed (`python tools/generate_products_csv.py 5000`):
   processed in the background while the app stays responsive.
4. *Audit log*: logins, failed logins, price changes, stock adjustments.
5. Swagger UI: every endpoint documented and testable with the JWT.

## Questions you can expect
- **Why not lock the row pessimistically?** The conditional update is a single statement: no lock
  held across application code, no retries, and correct across replicas.
- **What if Redis goes down?** Cache and rate limits degrade; stock and orders live in PostgreSQL.
- **How would you handle 100x traffic?** More replicas, read replicas for the catalog, CDN, and
  asynchronous processing of order events through a message broker.
- **How do you keep the AI honest?** The backend computes the numbers; the AI only writes the
  narrative from them, and every report shows the facts it was given.
