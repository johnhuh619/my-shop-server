# Suggested Commands

## Build & Run
```bash
# Build the project
gradlew.bat build

# Run the application
gradlew.bat bootRun

# Clean build artifacts
gradlew.bat clean
```

## Testing
```bash
# Run all tests
gradlew.bat test

# Run a single test class
gradlew.bat test --tests "com.minishop.project.minishop.payment.service.PaymentServiceTest"

# Run a specific test method
gradlew.bat test --tests "PaymentServiceTest.메서드명"
```

## System Utilities (Windows)
```bash
# Git
git status
git diff
git log --oneline -10

# File listing (PowerShell or cmd)
dir
dir /s   # recursive

# Search (use Serena tools instead where possible)
```

## Gradle Tasks
```bash
# View all available tasks
gradlew.bat tasks
```

## Notes
- Always use `gradlew.bat` on Windows (not `./gradlew`)
- H2 console available at `/h2-console` when app is running
- DB is in-memory (jdbc:h2:mem:minishop), resets on restart
