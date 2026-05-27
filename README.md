# Taptiles

Spring Boot deployment for the KP_2019 Taptiles game.

## Public URL

The canonical public game route is:

```text
/taptiles
```

The legacy `/taptiles-fedorco` route remains available as a compatibility alias.

## Render Environment

Set the datasource variables to the same Neon/Postgres database used by the
shared game system:

```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>/<database>?sslmode=require
SPRING_DATASOURCE_USERNAME=<user>
SPRING_DATASOURCE_PASSWORD=<password>
```

Render supplies `PORT`; production config binds Spring Boot to that value.
