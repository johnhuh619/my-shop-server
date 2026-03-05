# Use Case Sequence Diagrams (ASCII)

## 1) Create Order (reserve success/failure)

```text
Customer          OrderController      OrderService        InventoryService      InventoryRepo       OrderRepo
   |                    |                  |                     |                    |                 |
   | POST /api/orders   |                  |                     |                    |                 |
   |------------------->|                  |                     |                    |                 |
   |                    | createOrder()    |                     |                    |                 |
   |                    |----------------->|                     |                    |                 |
   |                    |                  | reserve(pid, qty)   |                    |                 |
   |                    |                  |-------------------->| lock + load        |                 |
   |                    |                  |                     |------------------->|                 |
   |                    |                  |                     |<-------------------|                 |
   |                    |                  |<--------------------| OK / shortage err  |                 |
   |                    |                  | save(Order CREATED) |                    |                 |
   |                    |                  |------------------------------------------------------------->|
   |                    |                  |<-------------------------------------------------------------|
   |                    |<-----------------|                     |                    |                 |
   |<-------------------| 200 OK / 4xx     |                     |                    |                 |
```

## 2) Confirm Payment Success (idempotent + async PAID transition)

```text
Customer        PaymentController    PaymentService    ConfirmHandler    PaymentRepo      Toss PG      PaymentEventListener    OrderService    InventoryService
   |                   |                 |                 |               |                |                |                   |                 |
   | POST /payments/confirm              |                 |               |                |                |                   |                 |
   |------------------>|                 |                 |               |                |                |                   |                 |
   |                   | confirmPayment  |                 |               |                |                |                   |                 |
   |                   |---------------> | prepareConfirm  |               |                |                |                   |                 |
   |                   |                 |---------------> | find+lock     |                |                |                   |                 |
   |                   |                 |                 |-------------> |                |                |                   |                 |
   |                   |                 |                 |<------------- | status         |                |                   |                 |
   |                   |                 |<--------------- | READY/COMPLETED              |                |                   |                 |
   |                   |                 | confirm external call            |-------------->|                |                   |                 |
   |                   |                 |<-------------------------------------------------| success         |                   |                 |
   |                   |                 | finalizeSuccess |               |                |                |                   |                 |
   |                   |                 |---------------> | mark COMPLETED + publish event|                |                   |                 |
   |                   |                 |                 |----------------------------------------------->| handle completed   |                 |
   |                   |                 |                 |                |                |                |------------------>| markAsPaid       |
   |                   |                 |                 |                |                |                |                   |---------------->|
   |                   |<----------------| Payment COMPLETED               |                |                |                   |                 |
   |<------------------| 200 OK          |                 |               |                |                |                   |                 |
```

## 3) Confirm Payment Failure (with compensation)

```text
Customer        PaymentController    PaymentService    Toss PG        ConfirmHandler    PaymentEventListener    OrderService    CompensationScheduler
   |                   |                 |               |                 |                   |                   |                   |
   | POST /payments/confirm              |               |                 |                   |                   |                   |
   |------------------>|                 |               |                 |                   |                   |                   |
   |                   |---------------> | confirm       |                 |                   |                   |                   |
   |                   |                 |-------------> |                 |                   |                   |                   |
   |                   |                 |<------------- | failed exception |                   |                   |                   |
   |                   |                 | finalizeFail  |                 |                   |                   |                   |
   |                   |                 |-------------------------------->| mark FAILED + event                   |                   |
   |                   |                 |                 |                |------------------>| handle failed      |                   |
   |                   |                 |                 |                |                   |------------------>| cancelOrderBySystem|
   |                   |                 |                 |                |                   |                   | release + CANCELED |
   |                   |<----------------| PG_CONFIRM_FAILED               |                   |                   |                   |
   |<------------------| error response  |               |                 |                   |                   |                   |
   |                   |                 |               |                 |                   |                   |                   |
   |                   |                 |               |                 |                   |                   |<------------------|
   |                   |                 |               |                 |                   |                   | retry(5m, idempotent)
```

## 4) Reserve timeout auto release (30m)

```text
OrderExpirationScheduler      OrderRepo               OrderService              InventoryService
          |                      |                        |                           |
          | every 1 min          |                        |                           |
          |--------------------->| find CREATED older 30m |                           |
          |<---------------------| expired orders         |                           |
          |------------------------------->| expireOrder(orderId)                    |
          |                                |--------------------------->| release(...)|
          |                                | status: CREATED -> EXPIRED |             |
```

## 5) Refund (request -> admin approve -> stock restore)

```text
User            RefundController      RefundService       AdminRefundController      PaymentService/Toss      RefundEventListener      InventoryService
 |                    |                   |                        |                         |                       |                    |
 | POST /refunds       |                   |                        |                         |                       |                    |
 |-------------------> | processRefund     |                        |                         |                       |                    |
 |                    |------------------> | validate + REQUESTED   |                         |                       |                    |
 |<------------------- | refund REQUESTED  |                        |                         |                       |                    |
 |                    |                   |                        |                         |                       |                    |
 |                    |                   | <--- Admin approve ---> |                         |                       |                    |
 |                    |                   |<----------------------- | approveRefund           |                       |                    |
 |                    |                   |------------------------>| cancelPayment           |                       |                    |
 |                    |                   |<------------------------| success                 |                       |                    |
 |                    |                   |-------------------------------------------------->| RefundCompletedEvent |                    |
 |                    |                   |                        |                         |                       |------------------->|
 |                    |                   |                        |                         |                       | addStock(...)      |
```

## 6) Admin delivery completion (COMPLETED)

```text
Admin              AdminOrderController            OrderService                   OrderRepo
  |                         |                          |                             |
  | POST /api/admin/orders/{id}/complete              |                             |
  |------------------------>| completeOrder(id)        |                             |
  |                         |------------------------->| findByIdWithLock            |
  |                         |                          |----------------------------->|
  |                         |                          |<-----------------------------|
  |                         |                          | already COMPLETED ? return   |
  |                         |                          | else PAID -> COMPLETED save  |
  |<------------------------| 200 OK                   |                             |
```
