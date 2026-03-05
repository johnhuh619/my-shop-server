# Use Case Sequence Diagrams (Text)

This document captures the current implementation flow as Mermaid `sequenceDiagram` text.

## 1) Create Order (reserve success/failure)

```mermaid
sequenceDiagram
    autonumber
    actor C as Customer
    participant OC as OrderController
    participant OS as OrderService
    participant PS as ProductService
    participant IS as InventoryService
    participant IR as InventoryRepository
    participant OR as OrderRepository

    C->>OC: POST /api/orders
    OC->>OS: createOrder(userId, items)

    loop each item (sorted by productId)
        OS->>PS: getProductById(productId)
        OS->>IS: reserve(productId, qty)
        IS->>IR: findByProductIdWithLock(productId)
        alt not enough stock
            IS-->>OS: BusinessException(INVENTORY_SHORTAGE)
            OS-->>OC: createOrder failed
            OC-->>C: 4xx error
        else reserved
            IS-->>OS: reserve success
        end
    end

    OS->>OR: save(Order CREATED + OrderItems)
    OR-->>OS: Order
    OS-->>OC: Order
    OC-->>C: 200 OK (CREATED)
```

## 2) Confirm Payment Success (idempotent, async paid transition)

```mermaid
sequenceDiagram
    autonumber
    actor C as Customer
    participant PC as PaymentController
    participant PVS as PaymentService
    participant PCH as PaymentConfirmHandler
    participant PR as PaymentRepository
    participant PG as PaymentGateway(Toss)
    participant EPL as PaymentEventListener(@Async)
    participant OS as OrderService
    participant IS as InventoryService
    participant OR as OrderRepository

    C->>PC: POST /api/payments/confirm
    PC->>PVS: confirmPayment(userId, paymentKey, tossOrderId, amount)
    PVS->>PCH: prepareConfirm(...)
    PCH->>PR: findByTossOrderId(+lock)

    alt already COMPLETED
        PCH-->>PVS: COMPLETED (idempotent return)
        PVS-->>PC: existing payment
        PC-->>C: 200 OK
    else ready to confirm
        PVS->>PG: confirmPayment(...) (external call)
        PG-->>PVS: success
        PVS->>PCH: finalizeConfirmSuccess(...)
        PCH->>PR: lock + mark COMPLETED + save
        PCH-->>EPL: publish PaymentCompletedEvent

        EPL->>OS: markAsPaid(orderId)
        OS->>OR: findByIdWithLock(orderId)
        OS->>IS: confirm(productId, qty) for each item
        OS->>OR: save(Order PAID)
    end

    PVS-->>PC: Payment COMPLETED
    PC-->>C: 200 OK
```

## 3) Confirm Payment Failure (reserve release compensation)

```mermaid
sequenceDiagram
    autonumber
    actor C as Customer
    participant PC as PaymentController
    participant PVS as PaymentService
    participant PCH as PaymentConfirmHandler
    participant PG as PaymentGateway(Toss)
    participant EPL as PaymentEventListener(@Async)
    participant OS as OrderService
    participant IS as InventoryService
    participant PCS as PaymentCompensationScheduler

    C->>PC: POST /api/payments/confirm
    PC->>PVS: confirmPayment(...)
    PVS->>PG: confirmPayment(...)
    PG-->>PVS: failed exception

    PVS->>PCH: finalizeConfirmFailure(...)
    PCH-->>EPL: publish PaymentFailedEvent
    EPL->>OS: cancelOrderBySystem(orderId)
    OS->>IS: release(productId, qty) for each item
    OS->>OS: order status CREATED -> CANCELED

    alt async handler failed partially
        Note over PCS,OS: every 5 min, FAILED payment older than 5 min
        PCS->>OS: cancelOrderBySystem(orderId) retry
        OS-->>PCS: idempotent no-op or success
    end

    PVS-->>PC: BusinessException(PG_CONFIRM_FAILED)
    PC-->>C: 4xx/5xx error
```

## 4) Reserve Timeout Auto Release (30 min)

```mermaid
sequenceDiagram
    autonumber
    participant OES as OrderExpirationScheduler
    participant OR as OrderRepository
    participant OS as OrderService
    participant IS as InventoryService

    loop every 1 min
        OES->>OR: find CREATED orders older than 30 min
        OR-->>OES: expired order list
        loop each expired order
            OES->>OS: expireOrder(orderId)
            OS->>IS: release(productId, qty) for each item
            OS->>OS: order status CREATED -> EXPIRED
        end
    end
```

## 5) Refund Flow (request -> admin approve -> stock restore)

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    actor A as Admin
    participant RC as RefundController
    participant ARC as AdminRefundController
    participant RS as RefundService
    participant PVS as PaymentService
    participant PG as PaymentGateway(Toss)
    participant REL as RefundEventListener(@Async)
    participant OS as OrderService
    participant IS as InventoryService

    U->>RC: POST /api/refunds
    RC->>RS: processRefund(userId, paymentId, items)
    RS->>OS: requestRefund(orderId) (if PAID)
    RS-->>U: Refund REQUESTED

    A->>ARC: POST /api/admin/refunds/{id}/approve
    ARC->>RS: approveRefund(refundId)
    RS->>PVS: cancelPayment(...)
    PVS->>PG: cancelPayment(...)
    PG-->>PVS: success
    RS-->>REL: publish RefundCompletedEvent

    REL->>OS: markAsRefunded(orderId) (full refund only)
    REL->>IS: addStock(productId, qty) for each refund item
```

## 6) Admin Order Completion (delivery complete)

```mermaid
sequenceDiagram
    autonumber
    actor A as Admin
    participant AOC as AdminOrderController
    participant OS as OrderService
    participant OR as OrderRepository

    A->>AOC: POST /api/admin/orders/{id}/complete (ROLE_ADMIN)
    AOC->>OS: completeOrder(orderId)
    OS->>OR: findByIdWithLock(orderId)
    alt already COMPLETED
        OS-->>AOC: existing order (idempotent return)
    else PAID
        OS->>OR: save(Order COMPLETED)
        OR-->>OS: updated order
        OS-->>AOC: updated order
    end
    AOC-->>A: 200 OK
```

## Notes

- Event transport is currently Spring `ApplicationEvent` + `@Async`, not DB Outbox.
- Payment completion path is idempotent at both payment and order transition levels.
- Reserve is not indefinite: it is released by `OrderExpirationScheduler` after 30 minutes (for `CREATED` orders).
