                                 EVENT-DRIVEN ARCHITECTURE (BEST PRACTICE)

                                       +----------------------+
                                       |   External Clients   |
                                       | Web / Mobile / APIs  |
                                       +----------+-----------+
                                                  |
                                                  v
                                      +------------------------+
                                      | API / Edge / Gateway   |
                                      | Auth, rate limit, ACL  |
                                      +-----------+------------+
                                                  |
                         Commands                 | synchronous validation
                +---------------------------------+----------------------------------+
                |                                                                    |
                v                                                                    v
      +----------------------+                                            +----------------------+
      |  Command / App Svc   |                                            |   Query / Read API   |
      |  validates intent    |                                            |  serves read models  |
      +----------+-----------+                                            +----------+-----------+
                 |                                                                   ^
                 | writes events + state changes                                      |
                 v                                                                   |
      +----------------------+         outbox / tx boundary              +----------------------+
      | Aggregate / Domain   |----------------------------------------->|   Read Models /      |
      | Business rules       |                                          | Projections / Views  |
      +----------+-----------+                                          +----------+-----------+
                 |                                                                 ^
                 | append immutable events                                          |
                 v                                                                 |
      +----------------------+                                                     |
      |  Event Store         |                                                     |
      | source of truth      |                                                     |
      | append-only stream   |                                                     |
      +----------+-----------+                                                     |
                 |                                                                 |
                 | publish durable events                                           |
                 v                                                                 |
      +----------------------+                                                     |
      | Message Broker / Bus |-----------------------------------------------------+
      | Kafka / NATS / etc.  |                 event subscriptions
      +----+-----------+-----+
           |           |           |                |                 |
           |           |           |                |                 |
           v           v           v                v                 v
 +----------------+ +----------------+ +----------------+ +----------------+ +----------------+
 | Service A      | | Service B      | | Service C      | | Notification   | | Analytics / BI |
 | idempotent     | | idempotent     | | idempotent     | | email/sms/push | | stream/ETL      |
 | consumers      | | consumers      | | consumers      | | consumers      | | consumers       |
 +--------+-------+ +--------+-------+ +--------+-------+ +--------+-------+ +--------+-------+
          |                  |                  |                  |                  |
          v                  v                  v                  v                  v
 +----------------+ +----------------+ +----------------+ +----------------+ +----------------+
 | Local DB /     | | Local DB /     | | Local DB /     | | Delivery logs  | | Warehouse /    |
 | cache / state  | | cache / state  | | cache / state  | | retries/DLQ    | | lake / marts   |
 +----------------+ +----------------+ +----------------+ +----------------+ +----------------+


 CROSS-CUTTING BEST PRACTICES

 +-----------------------------------------------------------------------------------------------+
 |  Observability: logs + metrics + tracing + correlation/causation IDs                         |
 |  Reliability: retries, backoff, dead-letter queue, idempotency, deduplication                |
 |  Governance: schema registry, versioned events, contracts, ownership                         |
 |  Security: authN/authZ, encryption, PII controls, audit trail                                 |
 |  Operations: replay support, poison message handling, consumer lag monitoring                 |
 +-----------------------------------------------------------------------------------------------+