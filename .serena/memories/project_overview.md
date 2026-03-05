# mini-shop Project Overview

## Purpose
E-commerce (쇼핑몰) application implementing a transaction + event-driven architecture for order, payment, and refund workflows.

## Tech Stack
- **Java 21** (toolchain enforced)
- **Spring Boot 4.0.0**
- **Spring Security** + JWT (jjwt 0.12.5)
- **Spring Data JPA** + H2 in-memory DB
- **Lombok** (compile-only)
- **Gradle** (wrapper)
- **Testing**: JUnit Platform, Awaitility (async event testing)

## Domains
| Domain     | Purpose                              |
|------------|--------------------------------------|
| auth       | JWT authentication/authorization     |
| user       | User management                      |
| product    | Product catalog (current state)      |
| inventory  | Stock management (concurrent resource)|
| order      | Purchase intent + order items        |
| payment    | Monetary transactions (idempotent)   |
| refund     | Reverse monetary flow                |
| outbox     | Event loss prevention bridge         |
| common     | Shared config, exceptions, utils     |

## Key Architecture Principles
- **Order ≠ Payment**: Order = intent, Payment = action
- **Snapshot immutability**: OrderItem copies Product data at order time
- **Idempotency**: `(user_id, idempotency_key)` UNIQUE on Payment
- **Inventory reservation**: Stock reserved at order creation, released on failure/expiry
- **Event-driven**: Spring Events for async processing (PG call, order status change)
- **DB is Source of Truth**: Outbox pattern for event delivery

## Cross-Domain Rules
- Order → Product: direct reference forbidden
- OrderItem → Product: snapshot only
- Payment → Order: no direct state modification
- Inventory access: only during Order creation
