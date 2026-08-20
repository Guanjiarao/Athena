# Athena Cognition

`athena-cognition` owns the user-confirmed cognition workflow. It is intentionally separate from
`athena-insight`, whose existing topics are content/recommendation features rather than a user's
private body question.

## Implemented V1 flow

```text
create clue -> create digest task -> fixed structured digest
-> accept / save knowledge / reject
-> topic + evidence + one action (accept only)
-> feedback -> topic version -> home aggregation
```

The fixed generator is a replaceable `DigestGenerator`. It does not call a model and never turns an
article mark or question into a confirmed body fact.

## Run locally

1. Create a MySQL 8 database and execute
   `athena-cognition-biz/src/main/resources/sql/cognition_v1.sql`.
2. Configure the shared Nacos `database.yaml`, or provide the datasource properties used by the
   other Athena services.
3. Start Nacos and the gateway, then run `AthenaCognitionApplication`.
4. Access `/v3/api-docs` or `/swagger-ui.html` on the service port.

Every mutation requires `Idempotency-Key`. The authenticated gateway supplies `userId`; public
request bodies and query parameters never accept it.

## Verification

```bash
mvn -pl athena-cognition/athena-cognition-biz -am test
```

The contract is in the repository root at `cognition-contract-v1.md`.
