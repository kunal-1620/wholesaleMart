# WholesaleMart B2B Clothing Commerce

WholesaleMart is a multi-tenant B2B clothing commerce web app. It is designed so the same hosted platform can serve multiple clothing businesses, each with its own business URL, owners/staff, customer list, catalog, inventory, orders, payment proofs, and analytics.

There is no public customer sign-up. A business owner/admin creates customer accounts, assigns phone/PIN credentials and customer tier, and can deactivate or change PINs whenever required.

## Tech stack

| Area | Technology / service | How we use it |
|---|---|---|
| Backend | Java 21 + Spring Boot 3.3.5 | Main web application, controllers, services, sessions, file handling, order workflow |
| UI | Thymeleaf + server-rendered HTML + CSS | Admin, customer, platform admin, cart, orders, analytics, product screens |
| Persistence | Spring Data JPA + Hibernate | Entity mapping and repository access |
| Production database | Neon PostgreSQL | Business data, users, products, inventory, orders, analytics data, and file metadata |
| Local database | H2 in-memory | Local development/demo data through the `local` Spring profile |
| Migrations | Flyway | Versioned database schema migrations under `src/main/resources/db/migration` |
| Hosting | Render Web Service | Builds and runs the Dockerized Spring Boot app from GitHub |
| Image/file storage | Cloudflare R2 | Stores product images, business logos, and payment proof screenshots for production uploads |
| R2 integration | AWS SDK for Java v2 S3 client | Uses R2’s S3-compatible API from the Spring Boot backend |
| PDF generation | OpenPDF | Generates downloadable order/invoice PDFs |
| Auth basics | Custom session auth + BCrypt PIN hash | Phone + PIN login, 2-hour session timeout, role/business scoped access |
| Security hardening | CSRF interceptor + login lockout | Protects POST forms and throttles repeated failed login attempts |
| Source/deploy trigger | GitHub | Render deploys from the `main` branch |

This is not a Next.js/React app. The current UI is server-rendered by Spring Boot using Thymeleaf.

## High-level architecture

```text
Developer pushes to GitHub main
        |
        v
Render Blueprint / Web Service
        |
        v
Docker build runs Spring Boot app
        |
        +--> Neon PostgreSQL
        |       - businesses
        |       - users / roles / customer tiers
        |       - products / colours / sizes / sets
        |       - inventory
        |       - orders / order items
        |       - stored file metadata
        |
        +--> Cloudflare R2
                - public bucket: product images, business logos
                - private bucket: payment proof screenshots
```

File access still goes through the app route `/files/{id}`:

- Old database-backed files are served from Neon.
- New R2 public files can redirect to the configured public R2 custom domain.
- Private payment screenshots are checked by the app before being streamed from R2.

This lets us move storage away from Neon without breaking older uploaded files.

## Main product capabilities

- Multi-business platform with platform admin/superadmin UI.
- Business-specific routes such as:
  - `/b/{businessSlug}/login`
  - `/b/{businessSlug}/admin`
  - `/b/{businessSlug}/shop`
  - `/b/{businessSlug}/cart`
  - `/b/{businessSlug}/orders`
- Roles:
  - `PLATFORM_ADMIN`: manages all businesses and can switch into business admin portals.
  - `BUSINESS_ADMIN`: business owner/admin.
  - `STAFF`: currently similar to owner/admin; can be restricted later.
  - `CUSTOMER`: can browse allowed catalog, cart, orders, and payment proof flow.
- Phone + PIN login.
- 2-hour session timeout.
- Admin-created customers only; no public sign-up.
- Customer tiers for product visibility.
- Business-specific product codes like `P0001`, `P0002`.
- Product categories, sizes, fixed size sets, colours, and colour-wise inventory.
- Fixed size set ordering where each size in a set is exactly one piece.
- Cart stock validation before order submission.
- Owner approval flow before payment.
- Manual payment proof using transaction ID and screenshot upload.
- Inventory is reduced only after owner/admin verifies payment.
- Scarce-stock handling: first verified payment wins; later orders can be marked out of stock with item-level shortage details.
- Customer/admin invoice PDF download.
- Owner analytics:
  - revenue
  - sets/pieces sold
  - order status counts
  - category demand
  - most demanded products
  - highly sold products
  - frequent out-of-stock items
  - customer activity

## Repository layout

```text
.
├── Dockerfile
├── render.yaml
├── pom.xml
├── readme.md
└── src
    ├── main
    │   ├── java/org/example
    │   │   ├── config        # auth/CSRF/web config, data bootstrap
    │   │   ├── controller    # platform/admin/shop/file controllers
    │   │   ├── domain        # JPA entities
    │   │   ├── repo          # Spring Data repositories
    │   │   ├── service       # business logic, orders, storage, analytics, PDFs
    │   │   └── session       # session DTOs/cart lines
    │   └── resources
    │       ├── db/migration  # Flyway migrations
    │       ├── static        # CSS and placeholder assets
    │       └── templates     # Thymeleaf UI templates
    └── test                  # service/security regression tests
```

## Run locally

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Open:

```text
http://localhost:8080
```

The local profile uses:

- H2 in-memory database.
- Seed demo data.
- Database-backed uploaded files.
- No Neon dependency.
- No Cloudflare R2 dependency.

## Local demo logins

These demo credentials are intended for local development only:

```text
Platform admin:   7000000000 / 0000
Owner/admin:      9999999999 / 1234 at /b/agarwal/login
Staff:            6666666666 / 2222 at /b/agarwal/login
Tier 1 customer:  8888888888 / 1111 at /b/agarwal/login
Tier 3 customer:  7777777777 / 3333 at /b/agarwal/login
```

Production demo data is disabled by default.

## Production services

### 1. GitHub

GitHub is the source of truth for the application code.

Current deployment flow:

```text
GitHub main branch -> Render auto deploy -> Docker build -> Spring Boot app
```

Render is configured from `render.yaml`.

### 2. Render

Render hosts the Spring Boot web app as a Docker-based web service.

`render.yaml` defines:

- service name: `wholesalemart`
- runtime: Docker
- region: Singapore
- Dockerfile path: `./Dockerfile`
- health check path: `/login`
- auto deploy from commits
- required production environment variables

Render does not store persistent application data. All persistent data goes to Neon and R2.

### 3. Neon PostgreSQL

Neon stores relational application data:

- businesses
- users and roles
- customer tiers
- categories
- sizes
- fixed size set templates
- products
- product colours
- inventory rows
- orders
- order items
- file metadata

Neon should not store new image bytes in production after R2 is enabled. The `stored_files` table keeps metadata and R2 object keys.

### 4. Cloudflare R2

Cloudflare R2 stores uploaded files in production.

We use two buckets:

| Bucket | Purpose | Access pattern |
|---|---|---|
| Public bucket | Product images and business logos | Public/custom-domain access, app can redirect |
| Private bucket | Payment proof screenshots | App checks authorization and streams file |

R2 is accessed using the AWS SDK for Java v2 through R2’s S3-compatible endpoint.

Important implementation details:

- Uploaded images are resized/compressed before storage.
- Max image dimension: `1200px`.
- Re-encoded JPEG quality: `0.78`.
- Metadata/EXIF is stripped by re-encoding.
- Replacing an image deletes the old stored file row and old R2 object.
- Deleting a product/colour also deletes related R2 object when allowed.
- Existing old database-backed files continue working.

## Production environment variables

### Required Render + Neon variables

Use a JDBC URL in Render, not Neon’s raw `postgresql://...` URL.

```text
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_DRIVER=org.postgresql.Driver
SPRING_DATASOURCE_URL=jdbc:postgresql://<neon-host>/<database>?sslmode=require
SPRING_DATASOURCE_USERNAME=<neon-user>
SPRING_DATASOURCE_PASSWORD=<neon-password>
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5
SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=0
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
```

### Required first platform admin variables

For a fresh production DB:

```text
APP_PLATFORM_ADMIN_PHONE=<your-platform-admin-phone>
APP_PLATFORM_ADMIN_PIN=<secure-pin>
APP_PLATFORM_ADMIN_NAME=Platform Admin
```

Without these, a fresh production database cannot create the first platform admin login.

### Optional bootstrap business variables

```text
APP_BOOTSTRAP_BUSINESS_NAME=WholesaleMart Platform
APP_BOOTSTRAP_BUSINESS_SLUG=platform
APP_BOOTSTRAP_CONTACT_PHONE=
APP_BOOTSTRAP_PAYMENT_INSTRUCTIONS=Manual payment. Upload transaction proof after approval.
```

### Cloudflare R2 variables

To enable R2:

```text
APP_STORAGE_BACKEND=r2
R2_ENDPOINT=https://<cloudflare-account-id>.r2.cloudflarestorage.com
R2_REGION=auto
R2_ACCESS_KEY_ID=<r2-access-key-id>
R2_SECRET_ACCESS_KEY=<r2-secret-access-key>
R2_PUBLIC_BUCKET=<bucket-for-product-images-and-logos>
R2_PRIVATE_BUCKET=<bucket-for-payment-screenshots>
R2_PUBLIC_BASE_URL=https://<your-r2-public-custom-domain>
```

`R2_PUBLIC_BASE_URL` is optional but recommended. It should point to the public custom domain connected to the public R2 bucket.

If `APP_STORAGE_BACKEND=database`, new uploads are stored in Neon as bytes. This is useful for local/dev fallback but is not recommended for production growth.

## How to link the services

### Step 1: Create Neon database

1. Create a Neon project.
2. Create or use the default database.
3. Copy the connection details.
4. Convert the URL to JDBC format:

```text
Raw Neon URL:
postgresql://user:password@host/database?sslmode=require

Render JDBC URL:
jdbc:postgresql://host/database?sslmode=require
```

Keep username/password in separate Render variables.

### Step 2: Create Cloudflare R2 buckets

Create two buckets:

```text
wholesalemart-public
wholesalemart-private
```

You can choose different names, but then set Render env vars accordingly.

Recommended:

- Public bucket for product images and logos.
- Private bucket for payment proofs.

### Step 3: Connect a public R2 custom domain

For production image delivery:

1. Add/manage your domain in Cloudflare.
2. Connect a custom domain to the public R2 bucket, for example:

```text
https://assets.yourdomain.com
```

3. Set:

```text
R2_PUBLIC_BASE_URL=https://assets.yourdomain.com
```

When product/logo files are requested through `/files/{id}`, the app can redirect to this public URL.

Payment screenshots do not use this public URL.

### Step 4: Create R2 API token

Create an R2 API token with Object Read & Write permission for the selected buckets.

Set in Render:

```text
R2_ACCESS_KEY_ID=...
R2_SECRET_ACCESS_KEY=...
R2_ENDPOINT=https://<cloudflare-account-id>.r2.cloudflarestorage.com
```

### Step 5: Create Render web service / blueprint

Use the repo:

```text
kunal-1620/wholesaleMart
```

Render reads:

```text
render.yaml
```

Set all secret values when Render asks for them.

For R2 production storage, change:

```text
APP_STORAGE_BACKEND=r2
```

The checked-in `render.yaml` keeps `APP_STORAGE_BACKEND=database` as a safe default so deploys do not fail before R2 credentials are added.

## File storage flow

### New public product/logo image

```text
Owner uploads image
    -> Spring Boot receives multipart file
    -> FileStorageService resizes/compresses image
    -> Uploads bytes to R2 public bucket
    -> Stores file metadata in Neon stored_files table
    -> Product/business record stores /files/{storedFileId}
    -> Browser later requests /files/{storedFileId}
    -> App validates session/business access
    -> App redirects to R2_PUBLIC_BASE_URL/object-key if configured
```

### New private payment proof screenshot

```text
Customer or owner uploads payment screenshot
    -> Spring Boot receives multipart file
    -> FileStorageService resizes/compresses image
    -> Uploads bytes to R2 private bucket
    -> Stores file metadata in Neon stored_files table
    -> Order stores /files/{storedFileId}
    -> Browser later requests /files/{storedFileId}
    -> App checks user is owner/staff/platform admin or matching order customer
    -> App streams file from private R2 bucket
```

### Old database-backed file

```text
Browser requests /files/{storedFileId}
    -> App checks permissions
    -> App serves bytes from Neon stored_files.data
```

## Database migrations

We use Flyway for schema changes.

Migration files live here:

```text
src/main/resources/db/migration
```

Current migrations:

```text
V1__initial_schema.sql
V2__stored_file_metadata.sql
```

Important rules:

- Do not edit an already-applied migration after it has been deployed.
- Add a new `V3__...sql`, `V4__...sql`, etc. for future schema changes.
- Production uses:

```text
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
```

Hibernate validates that entities match the schema, but Flyway owns schema creation/changes.

### Existing Neon database behavior

`spring.flyway.baseline-on-migrate=true` allows Flyway to safely baseline an existing database that was originally created before Flyway was introduced.

For new empty databases:

```text
Flyway applies V1, then V2, then later migrations.
```

For existing databases:

```text
Flyway baselines existing schema, then applies new migrations that are not already represented.
```

If a migration fails in production, inspect Render logs and Neon schema state before retrying. Do not manually modify migration files that have already been applied.

## Deployment process

Normal deploy:

```bash
git push origin main
```

Render auto-deploys from GitHub.

Manual redeploy:

1. Open Render dashboard.
2. Open the `wholesalemart` service.
3. Go to Deploys.
4. Click Manual Deploy.
5. Choose Deploy latest commit.

If dependency/build cache looks stale, use:

```text
Clear build cache & deploy
```

## Testing and verification

Run tests:

```bash
mvn test
```

Build package:

```bash
mvn -DskipTests package
```

Useful checks before pushing:

```bash
git diff --check
mvn test
mvn -DskipTests package
```

## Security notes

Implemented:

- Business-scoped URLs.
- Role-based route protection.
- Tenant/business ownership checks in service methods.
- BCrypt PIN hashing.
- 2-hour session timeout.
- CSRF token validation for POST forms.
- Failed-login lockout after repeated wrong PIN attempts.
- Private payment screenshot authorization.

Still recommended later:

- Move from custom auth/interceptor to full Spring Security if the product grows.
- Stronger PIN policy or password/OTP option.
- External monitoring/log alerts.
- Automated end-to-end browser tests.
- More granular staff permissions.

## Useful service docs

- Render: https://render.com/docs
- Neon: https://neon.com/docs
- Cloudflare R2: https://developers.cloudflare.com/r2/
- Cloudflare R2 S3 API: https://developers.cloudflare.com/r2/get-started/s3/
- Cloudflare R2 Java SDK example: https://developers.cloudflare.com/r2/examples/aws/aws-sdk-java/
