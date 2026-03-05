# Codebase Structure

## Source Layout
```
src/main/java/com/minishop/project/minishop/
├── MiniShopApplication.java          # Entry point
├── auth/                             # Authentication (JWT)
│   ├── controller/AuthController
│   ├── domain/AuthenticatedUser, TokenPayload
│   ├── dto/LoginRequest, LoginResponse
│   └── service/AuthService, TokenService
├── user/                             # User management
│   ├── controller/UserController
│   ├── domain/User, UserRole, UserStatus
│   ├── dto/UserRegisterRequest, UserResponse
│   └── service/UserService, repository/UserRepository
├── product/                          # Product catalog
│   ├── controller/ProductController
│   ├── domain/Product, ProductStatus
│   ├── dto/CreateProductRequest, ProductResponse
│   └── service/ProductService, repository/ProductRepository
├── inventory/                        # Stock management
│   ├── controller/InventoryController
│   ├── domain/Inventory
│   ├── dto/AddStockRequest, InventoryResponse
│   └── service/InventoryService, repository/InventoryRepository
├── order/                            # Order processing
│   ├── controller/OrderController
│   ├── domain/Order, OrderItem, OrderStatus
│   ├── dto/CreateOrderRequest, OrderItemRequest, OrderResponse, OrderItemResponse
│   ├── scheduler/OrderExpirationScheduler
│   └── service/OrderService, repository/OrderRepository
├── payment/                          # Payment processing
│   ├── controller/PaymentController
│   ├── domain/Payment, PaymentStatus
│   ├── dto/CreatePaymentRequest, PaymentResponse
│   ├── event/PaymentCreatedEvent, PaymentCompletedEvent, PaymentFailedEvent, PaymentEventListener
│   ├── gateway/PaymentGateway (interface), DefaultPaymentGateway
│   └── service/PaymentService, repository/PaymentRepository
├── refund/                           # Refund processing
│   ├── controller/RefundController, AdminRefundController
│   ├── domain/Refund, RefundItem, RefundStatus
│   ├── dto/CreateRefundRequest, RefundItemRequest, ApproveRefundRequest, RejectRefundRequest, RefundResponse, RefundItemResponse
│   ├── event/RefundCompletedEvent, RefundEventListener
│   └── service/RefundService, repository/RefundRepository
├── outbox/                           # Event bridge (skeleton)
│   └── (package-info.java files only)
└── common/                           # Shared infrastructure
    ├── config/AsyncConfig, SecurityConfig
    ├── exception/BusinessException, ErrorCode
    ├── filter/JwtAuthenticationFilter
    ├── response/ApiResponse
    └── util/AuthenticationContext, DateTimeUtil
```

## Test Layout
```
src/test/java/com/minishop/project/minishop/
├── MiniShopApplicationTests.java
├── inventory/domain/InventoryTest, service/InventoryConcurrencyTest
├── order/domain/OrderTest, OrderItemTest, service/OrderServiceTest
├── payment/domain/PaymentTest, service/PaymentServiceTest, PaymentIdempotencyTest, TestPaymentGateway
└── refund/domain/RefundTest, service/RefundServiceTest
```

## Configuration
- `src/main/resources/application.properties` - H2 in-memory DB, JWT config
- `build.gradle` - Dependencies and build config
- `docs/` - Architecture docs (ARCHITECTURE.md, DOMAIN_RULES.md, PACKAGE_RULES.md, TEST_RULE.md, etc.)
