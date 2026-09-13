# Round1 B2B Clothing Commerce

A Spring Boot web app for a multi-tenant B2B clothing business portal.

## Run locally

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Open:

```text
http://localhost:8080
```

The local profile uses an in-memory H2 database and DB-backed uploaded files. No local database file or local upload directory is required.

## Demo logins

```text
Platform admin:   7000000000 / 0000
Owner/admin:      9999999999 / 1234 at /b/agarwal/login
Staff:            6666666666 / 2222 at /b/agarwal/login
Tier 1 customer:  8888888888 / 1111 at /b/agarwal/login
Tier 3 customer:  7777777777 / 3333 at /b/agarwal/login
```

There is no public sign-up. Admins create customers, set/change PINs, assign tiers, and can deactivate customers at any time.

## Implemented

- Platform admin common login plus business-specific owner, staff, and customer login with a 2-hour session timeout.
- Business-aware data model for reusable multi-business deployment.
- Business-specific routes such as `/b/agarwal/login`, `/b/agarwal/admin`, `/b/agarwal/shop`, `/b/agarwal/cart`, and `/b/agarwal/orders`.
- Platform admin/superadmin UI for adding businesses, editing business payment/contact details, adding business owners, and switching into any business admin portal.
- Staff role for business-scoped staff users. Staff currently has the same business-admin portal access as owners; permissions can be restricted later.
- Mandatory unique business slug for tenant-specific URLs such as `/b/agarwal/login`.
  Slugs are normalized to lowercase letters, numbers, and hyphens, and duplicate slugs are rejected before saving.
- Mandatory business logo upload on each business record. Existing/demo businesses are backfilled with a placeholder logo, and admin/customer screens show the business logo and name in the top-left header.
- Admin product management with category, description, same price for all visible tiers, and tier-based visibility.
- Business-specific product IDs such as `P0001`, `P0002`, etc. Product IDs are unique within each business and are shown on the admin product list/form; the database still keeps a separate internal primary key.
- Product records track created date so catalogs can default to recently added products first.
- Business-managed product categories with a unique category name per business. Product form uses a category dropdown.
- Business-managed sizes with unique size labels. Every business is seeded/backfilled with `34 (XS)`, `36 (S)`, `38 (M)`, `40 (L)`, `42 (XL)`, and `44 (XXL)`.
- Business-managed fixed size sets built from a multi-select size dropdown. Product form uses a multi-select fixed-set dropdown.
- Product colour variants with separate colour-wise inventory.
- Fixed size sets where every listed size counts as exactly 1 piece.
- Customer add-to-cart supports selecting multiple fixed sets at once, with a separate quantity for each selected set and combined stock validation for overlapping sizes.
- Customer catalog filtered by customer tier.
- Customer catalog cards show product ID, name, category, description, primary product image from the first colour image, and individual item price. Tier details are hidden from customer screens.
- Customer catalog supports search by product ID/name, category filter, colour filter, fixed set/size filter, in-stock-only filter, item price range, and sorting by recently added, product ID, name, price, category, or most demanded/popular.
- Product price is treated as the individual item price. Cart/order set price is calculated as item price × number of sizes in the selected fixed set.
- Cart and order request flow.
- Cart submission stock validation: if the customer waited and stock changed, the order is blocked and exact product/colour/size shortages are shown.
- Admin approve/reject order flow.
- Admin approval stock validation: owner sees current stock warnings before approval and cannot approve unavailable items.
- Admin order editing before final payment: owner can update quantities, remove unavailable lines, and add replacement products/colours/size sets.
- Manual payment proof via transaction ID and screenshot upload.
- Owner-side payment entry: owner can enter/update transaction details and upload a payment screenshot after approval when payment arrives outside the portal.
- Inventory deduction only after admin verifies payment.
- Scarce-stock handling: the first verified payment gets the stock; later verification attempts mark the order as out of stock and show item-level size shortages to both admin and customer.
- Invoice PDF download from both customer and admin order detail pages.
- Owner analytics page with date-range filters, revenue, sets/pieces sold, order status counts, category demand, most demanded products, highly sold products, frequent out-of-stock items, and customer activity.
- Per-customer activity page with order history, confirmed spend, last order time, and account details.

## Notes

## Hosting configuration

Production is configured through environment variables. The repo includes a Dockerfile and `render.yaml` for a Render Blueprint deployment with one web service and one managed PostgreSQL database.

### Render deployment

You do not need Docker installed locally. The Dockerfile is used by Render's cloud build environment when it deploys from GitHub.

1. Push this repo to GitHub.
2. In Render, create a new Blueprint from `kunal-1620/wholesaleMart`.
3. Render will read `render.yaml`, build the Docker image, and create the `wholesalemart-db` PostgreSQL database.
4. When Render prompts for secret values, set:

```text
APP_PLATFORM_ADMIN_PHONE=...
APP_PLATFORM_ADMIN_PIN=...
```

5. After deploy, open the Render app URL and log in at `/login` with the platform admin phone and PIN.

The Render blueprint injects database connection values through `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, and `DB_PASSWORD`. If you host somewhere else, set those variables or override the standard Spring datasource properties with your provider's equivalent values.

Demo data is disabled by default in production. `APP_PLATFORM_ADMIN_PHONE` and `APP_PLATFORM_ADMIN_PIN` are required for a fresh hosted database so the first platform admin can log in.

Uploaded business logos, product images, and payment proof screenshots are stored in the application database and served through authenticated file routes. This avoids local filesystem image storage for the initial hosted version.

Longer term, if upload volume grows, move file storage from database-backed files to S3/R2/Cloudinary while keeping the same stored URL pattern in the business/product/order records.
