# Shared Game Hub Deployment

Tower Defense is the shared front door because it already owns login, OAuth,
session cookies, and the Render/Neon database connection.

## Database

The Tower Defense Render service uses `DATABASE_URL`, documented in
`C:\Users\fedor\Desktop\Tower Defense\DEPLOY_RENDER.md`. That value points to
the Neon Postgres database. KP_2019 can use the same database by setting these
Spring environment variables in its deployment:

```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://<neon-host>/<database>?sslmode=require
SPRING_DATASOURCE_USERNAME=<neon-user>
SPRING_DATASOURCE_PASSWORD=<neon-password>
```

Do not commit the Neon connection string or password.

## Shared Landing Page

Set `TAPTILES_URL` on the Tower Defense Render service to the public KP_2019
Taptiles URL. If KP_2019 is reverse-proxied under the same Render domain, use:

```properties
TAPTILES_URL=/taptiles
```

If KP_2019 is deployed separately, use the full HTTPS URL.

## Auth Integration

The shared account tables are `app_user` and `app_identity` in the Tower Defense
database. Tower Defense also exposes Taptiles score endpoints:

- `GET /api/taptiles/leaderboard`
- `POST /api/taptiles/score`

The POST endpoint requires the Tower Defense session cookie and CSRF token. To
make KP_2019 fully share login, deploy it under the same site as Tower Defense
or add JWT verification in Spring using the same `JWT_SECRET`.
