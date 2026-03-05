# Task Completion Checklist

## Before Submitting Code Changes

### 1. Verify Build
```bash
gradlew.bat build
```

### 2. Run Tests
```bash
gradlew.bat test
```

### 3. Rule Compliance Check
- [ ] Package location follows `docs/PACKAGE_RULES.md`
- [ ] Domain rules from `docs/DOMAIN_RULES.md` are not violated
- [ ] Architecture boundaries from `docs/ARCHITECTURE.md` are respected
- [ ] No snapshot data mutations (OrderItem, Payment, Refund amounts)
- [ ] No cross-domain rule violations
- [ ] Dependency direction rules maintained

### 4. Forbidden Actions Check
- [ ] No unrelated code refactored
- [ ] No architecture/domain boundary changes
- [ ] No new dependencies introduced (except approved: Awaitility)
- [ ] No Spring Security config modifications
- [ ] No validation/idempotency bypasses
- [ ] No package moves/renames
- [ ] Events not assumed to process only once

### 5. Transaction Safety
- [ ] Outbox records created inside DB transactions
- [ ] Workers are idempotent and duplication-safe
- [ ] Long/slow operations are async

### 6. If Unclear
- STOP and ASK before coding
