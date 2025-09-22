# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

### Build the library and demo app
```bash
./gradlew build
```

### Run tests
```bash
# Run all unit tests
./gradlew test

# Run tests for lib module only
./gradlew :lib:test

# Run tests for app module only
./gradlew :app:test

# Run a single test class
./gradlew :lib:testDebugUnitTest --tests "net.spooncast.openmocker.lib.core.MockingEngineTest"

# Run instrumentation tests on connected devices
./gradlew connectedAndroidTest
```

### Linting and Code Quality
```bash
# Run lint checks
./gradlew lint

# Auto-fix lint issues where possible
./gradlew lintFix

# Run all verification tasks (tests + lint)
./gradlew check
```

### Publishing
```bash
# Publish library to local repository
./gradlew publishToMavenLocal
```

## Architecture Overview

### Core Architecture

This is an Android HTTP mocking library built using **Clean Architecture** patterns with multiple HTTP client adapters.

**Key Architectural Components:**

1. **Generic Mocking Engine** (`lib/src/main/java/net/spooncast/openmocker/lib/core/`)
   - `MockingEngine<TRequest, TResponse>`: Core generic engine that handles mocking logic independent of HTTP client implementation
   - `HttpClientAdapter<TRequest, TResponse>`: Generic interface for adapting different HTTP clients (OkHttp, Ktor)
   - `HttpRequestData` / `HttpResponseData`: Client-agnostic data models

2. **HTTP Client Adapters** (`lib/src/main/java/net/spooncast/openmocker/lib/client/`)
   - `okhttp/OkHttpAdapter`: Adapter for OkHttp integration via `OpenMockerInterceptor`
   - `ktor/` (future): Planned Ktor client support
   - Each adapter implements `HttpClientAdapter` to provide type-safe integration

3. **Repository Layer** (`lib/src/main/java/net/spooncast/openmocker/lib/repo/`)
   - `CacheRepo`: Interface for caching and mocking HTTP responses
   - `MemoryCacheRepo`: In-memory implementation of cache repository

4. **UI Layer** (`lib/src/main/java/net/spooncast/openmocker/lib/ui/`)
   - Jetpack Compose UI for mock management
   - `OpenMockerActivity`: Main activity for managing mocks
   - Common UI components for displaying and editing API mocks

### Project Structure

- **`lib/`**: Main library module containing the OpenMocker SDK
- **`app/`**: Demo application showcasing library usage with weather API example
- **`core/`**: Moved to lib - contains generic mocking engine and adapters
- **`okhttp/` & `ktor/`**: Legacy modules, functionality moved to `lib/src/main/java/.../client/`

### Key Design Patterns

1. **Generic Type System**: The `MockingEngine<TRequest, TResponse>` uses generics to support multiple HTTP clients through a unified interface

2. **Adapter Pattern**: `HttpClientAdapter` abstracts different HTTP client implementations while maintaining type safety

3. **Repository Pattern**: `CacheRepo` provides abstraction for cache storage, currently implemented as in-memory but extensible

4. **Clean Architecture**: Clear separation between core logic, adapters, and UI with dependency inversion

### Testing Framework

- Uses **JUnit 5** for unit tests (configured in lib module)
- MockK for mocking in Kotlin tests
- Coroutines testing support for async operations
- Test structure follows production code organization

### Current Branch Context

Working on `step3_support_ktor_client` branch - implementing Ktor HTTP client support following the existing OkHttp adapter pattern.

### OpenMockerInterceptor Core Functionality

**Purpose**: HTTP response mocking interceptor for development and testing environments.

**Key Features**:
1. **HTTP Request Interception**: Intercepts all OkHttp requests and returns mock responses when available
2. **Automatic Response Caching**: Caches real network responses for future mocking use
3. **Network Delay Simulation**: Configurable delays to simulate real network conditions
4. **Runtime Mock Management**: UI-driven mock configuration during development

**Operation Flow**:
```
HTTP Request → OpenMockerInterceptor → MockingEngine.getMockData()
    ↓ (if mock exists)
Mock Response + Delay ← createMockResponse()
    ↓ (if no mock)
Real Network Call → Cache Response → Return Real Response
```

**Architecture**:
```
OpenMockerInterceptor (OkHttp Layer)
        ↓
MockingEngine<Request, Response> (Core Layer)
        ↓
OkHttpAdapter (Adapter Layer) + MemCacheRepoImpl (Repository Layer)
```

### Library Usage Pattern

```kotlin
// Basic usage - get OkHttp interceptor
val interceptor = OpenMocker.getInterceptor()
val okHttpClient = OkHttpClient.Builder()
    .addInterceptor(interceptor)
    .build()

// Show UI for managing mocks
OpenMocker.show(context)

// Show persistent notification for easy access
OpenMocker.showNotification(activity)
```

**Common Use Cases**:
- API server instability during development
- Specific error scenario testing
- Offline development and testing
- UI prototyping with fast mock responses
- Network condition simulation with configurable delays

## Development Rules

### Task Scope and Requirements Compliance

**IMPORTANT**: Always strictly adhere to the specified task requirements and scope. Do not exceed or deviate from the defined boundaries.

#### Task Analysis Protocol

1. **Requirement Verification**: Before starting any implementation:
   - Read and analyze the specific task requirements completely
   - Check GitHub issues for exact scope and deliverables
   - Identify prerequisites and dependencies
   - Confirm what should NOT be implemented in the current task

2. **Scope Boundaries**:
   - **DO**: Only implement what is explicitly requested in the task
   - **DO**: Follow the exact specifications and constraints provided
   - **DO**: Check existing implementations to avoid duplication
   - **DO NOT**: Add extra features or "nice to have" functionality
   - **DO NOT**: Implement future phases or adjacent requirements
   - **DO NOT**: Assume additional scope beyond what is documented

3. **Pre-Implementation Checklist**:
   - [ ] Task requirements clearly understood and documented
   - [ ] Existing codebase analyzed for related implementations
   - [ ] Dependencies and prerequisites identified and verified
   - [ ] Scope boundaries confirmed (what to do vs. what NOT to do)
   - [ ] GitHub issue status checked for current phase requirements

4. **Implementation Validation**:
   - Only proceed with implementation after scope verification
   - Regularly cross-reference deliverables with original requirements
   - Document any deviations or clarifications needed
   - Validate that implementation matches exactly what was requested

#### Example Violation Prevention:
- ❌ **Wrong**: Implementing Phase 1.2 requirements when Phase 1.1 is already complete
- ✅ **Correct**: Checking existing implementations before starting new work
- ❌ **Wrong**: Adding extra features beyond task scope "for completeness"
- ✅ **Correct**: Implementing only what is explicitly requested in the task

**Consequence of Violations**: Scope creep leads to duplicated work, confusion, and potential conflicts with existing implementations.

## Development Notes

- **Kotlin Version**: 17 JVM target
- **Android**: Minimum SDK 28, Compile SDK 35
- **Compose**: Enabled with strong skipping mode
- **Dependencies**: OkHttp, Jetpack Compose, Navigation, Coroutines, Serialization
- **Publishing**: Maven publication configured for `net.spooncast:openmocker:0.0.13`