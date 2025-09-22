# OpenMockerPlugin for Ktor 개발 계획

## 🎯 개발 목표
OkHttp의 OpenMockerInterceptor와 동일한 기능을 Ktor client에서 제공하는 커스텀 플러그인 개발

## 📋 주요 구성 요소

### 1. **OpenMockerPlugin 클래스**
- Ktor 공식 `createClientPlugin()` 사용
- 설정 가능한 플러그인으로 구현
- `onRequest` 핸들러에서 모킹 처리 및 네트워크 호출 생략

### 2. **KtorAdapter 클래스**
- `HttpClientAdapter<HttpRequestData, HttpResponse>` 구현
- Ktor의 `HttpRequestData`, `HttpResponse` 객체와 generic 데이터 모델 간 변환
- `HttpResponseData`를 활용한 완전한 HttpResponse 생성

### 3. **OpenMockerPluginConfig 클래스**
- `enabled: Boolean` 설정만 포함 (간소화)
- MockingEngine 인스턴스 보유

## 🏗️ 아키텍처 설계

```
HttpClient + OpenMockerPlugin
        ↓
KtorAdapter (HttpRequestData ↔ HttpResponse)
        ↓
MockingEngine<HttpRequestData, HttpResponse> (기존 API 활용)
        ↓
MemCacheRepoImpl (공통 저장소, 동기화 처리 생략)
```

## 💾 파일 구조

```
lib/src/main/java/net/spooncast/openmocker/lib/client/ktor/
├── OpenMockerPlugin.kt        # 메인 플러그인 클래스
├── KtorAdapter.kt            # Ktor 어댑터 구현
└── OpenMockerPluginConfig.kt  # 플러그인 설정 클래스 (enabled만)
```

## 🔧 핵심 구현 사항

### **OpenMockerPlugin.kt**
```kotlin
val OpenMockerPlugin = createClientPlugin("OpenMocker", ::OpenMockerPluginConfig) {
    onRequest { request, _ ->
        if (!pluginConfig.enabled) return@onRequest

        val mockData = pluginConfig.mockingEngine.getMockData(request)
        if (mockData != null) {
            // Option B: Plugin에서 직접 delay 처리
            if (mockData.duration > 0) {
                delay(mockData.duration)
            }
            // 실제 네트워크 호출 생략하고 mock response 반환
            val mockResponse = pluginConfig.mockingEngine.createMockResponse(request, mockData)
            return@onRequest mockResponse
        }
    }

    onResponse { response ->
        if (pluginConfig.enabled) {
            pluginConfig.mockingEngine.cacheResponse(response.request, response)
        }
    }
}
```

### **KtorAdapter.kt**
```kotlin
internal class KtorAdapter : HttpClientAdapter<HttpRequestData, HttpResponse> {
    override val clientType: String = "Ktor"

    override fun extractRequestData(clientRequest: HttpRequestData): HttpRequestData {
        return HttpRequestData(
            method = clientRequest.method.value,
            path = clientRequest.url.encodedPath,
            url = clientRequest.url.toString(),
            headers = clientRequest.headers.toMap()
        )
    }

    override fun createMockResponse(originalRequest: HttpRequestData, mockResponse: CachedResponse): HttpResponse {
        // Option A: HttpResponseData를 활용한 완전한 HttpResponse 생성
        return HttpResponseData(
            statusCode = HttpStatusCode.fromValue(mockResponse.code),
            requestTime = GMTDate.now(),
            headers = Headers.Empty,
            version = HttpProtocolVersion.HTTP_2_0,
            body = ByteReadChannel(mockResponse.body),
            callContext = coroutineContext
        )
    }

    override fun extractResponseData(clientResponse: HttpResponse): HttpResponseData {
        // Option A: HttpResponse.content를 ByteReadChannel로 복사해서 처리
        val bodyText = runBlocking {
            clientResponse.bodyAsText()
        }

        return HttpResponseData(
            code = clientResponse.status.value,
            body = bodyText,
            headers = clientResponse.headers.toMap(),
            isSuccessful = clientResponse.status.isSuccess()
        )
    }
}
```

### **OpenMockerPluginConfig.kt**
```kotlin
class OpenMockerPluginConfig {
    var enabled: Boolean = true

    internal val mockingEngine: MockingEngine<HttpRequestData, HttpResponse> by lazy {
        val cacheRepo = MemCacheRepoImpl.getInstance()
        val adapter = KtorAdapter()
        MockingEngine(cacheRepo, adapter)
    }
}
```

## 🚀 사용법

```kotlin
val client = HttpClient {
    install(OpenMockerPlugin) {
        enabled = true
    }
}

// 기존 OpenMocker UI와 연동
OpenMocker.show(context) // 동일한 캐시 공유
```

## 📦 의존성 추가

**build.gradle.kts**에 Ktor client 의존성:
```kotlin
implementation("io.ktor:ktor-client-core:2.3.+")
```

## 🧪 테스트 전략

### **KtorAdapterTest.kt**
- Ktor 객체 생성 및 변환 로직 테스트
- Response body 복사 로직 검증
- MockK 활용한 단위 테스트

### **OpenMockerPluginTest.kt**
- 플러그인 설치 및 동작 테스트
- onRequest에서 모킹 동작 확인
- delay 처리 및 네트워크 호출 생략 검증

## 🔄 기존 코드와의 통합

- **MockingEngine**: 기존 API 그대로 활용 (getMockData, createMockResponse, cacheResponse)
- **MemCacheRepoImpl**: OkHttp와 동일한 저장소 공유
- **UI 레이어**: 기존 OpenMockerActivity 그대로 활용
- **Thread Safety**: 동기화 처리 생략

## 🎁 예상 결과

Ktor 사용자가 OpenMocker를 OkHttp와 동일하게 사용하며, 두 HTTP 클라이언트가 동일한 Mock 데이터를 공유하는 개발 환경

---

# 📋 TDD 기반 세부 Task 분해 계획

## 🔧 구현 결정사항

### **주요 기술적 결정**
1. **Ktor Version**: `io.ktor:ktor-client-core:2.3.+` 지원
2. **Coroutine 처리**: `runBlocking { clientResponse.bodyAsText() }` 사용으로 기존 인터페이스 호환성 유지
3. **에러 처리**: OkHttp와 동일한 방식 적용, Ktor 특화 처리 없음
4. **Thread Safety**: 추후 고려사항으로 분류
5. **테스트 전략**: Ktor 컴포넌트 모킹한 단위 테스트에 집중

## 🏗️ Task 분해 계획

### **Phase 1: Core Architecture & Data Types (TDD)**

**Task 1.1: KtorAdapter Interface Implementation Tests** ⏱️ 1.5h
- **Test First**: Write `KtorAdapterTest.kt` with comprehensive scenarios matching `OkHttpAdapterTest.kt` patterns
- **Implementation**: Create `KtorAdapter.kt` implementing `HttpClientAdapter<HttpRequestData, HttpResponse>`
- **Key Focus**: Ktor `HttpRequestData`/`HttpResponse` type handling with proper mocking
- **DoD**: All interface methods tested, 90%+ coverage, type validation working

**Task 1.2: Ktor Type Conversion Logic Tests** ⏱️ 2h
- **Test First**: Test Ktor `HttpRequestData` → generic `HttpRequestData` conversion
- **Test First**: Test `CachedResponse` → Ktor `HttpResponse` creation using `HttpResponseData`
- **Test First**: Test Ktor `HttpResponse` → `HttpResponseData` extraction using `runBlocking { bodyAsText() }`
- **Implementation**: Core conversion with proper error handling (same as OkHttp style)
- **DoD**: All conversion paths tested, handles edge cases, no custom Ktor exceptions

### **Phase 2: Plugin Configuration & Setup**

**Task 2.1: OpenMockerPluginConfig Tests** ⏱️ 1h
- **Test First**: Write `OpenMockerPluginConfigTest.kt`
- **Implementation**: Simple config class with `enabled: Boolean` and lazy MockingEngine initialization
- **Coverage**: MockingEngine creation, MemCacheRepoImpl.getInstance() integration
- **DoD**: Config initializes correctly, shares cache with OkHttp

**Task 2.2: Plugin Registration & Basic Structure Tests** ⏱️ 1.5h
- **Test First**: Test plugin installation using `createClientPlugin("OpenMocker", ::OpenMockerPluginConfig)`
- **Implementation**: Basic `OpenMockerPlugin` structure with config binding
- **Coverage**: Plugin registration, config access, lifecycle management
- **DoD**: Plugin installs on HttpClient without errors

### **Phase 3: Core Mocking Logic (TDD)**

**Task 3.1: onRequest Handler Mock Logic Tests** ⏱️ 2h
- **Test First**: Test `mockingEngine.getMockData()` integration
- **Test First**: Test delay simulation with `delay(mockData.duration)`
- **Test First**: Test network call bypass when mock exists
- **Test First**: Test `mockingEngine.createMockResponse()` call
- **Implementation**: Complete `onRequest` handler matching Task.md specification
- **DoD**: Mock flow works, delays handled, real calls bypassed correctly

**Task 3.2: onResponse Handler Caching Tests** ⏱️ 1.5h
- **Test First**: Test real response caching via `mockingEngine.cacheResponse()`
- **Test First**: Test enabled/disabled behavior
- **Implementation**: `onResponse` handler for caching real responses
- **DoD**: Real responses cached for future mocking use

### **Phase 4: Integration & Validation**

**Task 4.1: End-to-End Plugin Integration Tests** ⏱️ 2h
- **Test First**: Full workflow test with mocked HttpClient and engine responses
- **Test First**: Mock → Real → Cache cycle validation
- **Implementation**: Complete plugin integration with all handlers
- **DoD**: Plugin works seamlessly with mocked Ktor components

**Task 4.2: Cross-Client Cache Sharing Validation** ⏱️ 1h
- **Test First**: Test OkHttp cached data accessible from Ktor
- **Test First**: Test Ktor cached data accessible from OkHttp
- **Implementation**: Verify shared MemCacheRepoImpl behavior
- **DoD**: Both clients access same cached responses via shared singleton

## 📁 구현 파일 구조

### **Production Code**
```
lib/src/main/java/net/spooncast/openmocker/lib/client/ktor/
├── OpenMockerPlugin.kt        # createClientPlugin() implementation
├── KtorAdapter.kt            # HttpClientAdapter<HttpRequestData, HttpResponse>
└── OpenMockerPluginConfig.kt  # Simple config with enabled flag
```

### **Test Code**
```
lib/src/test/java/net/spooncast/openmocker/lib/client/ktor/
├── OpenMockerPluginTest.kt
├── KtorAdapterTest.kt
└── OpenMockerPluginConfigTest.kt
```

## 🔗 Task Dependencies

```
Task 1.1 → Task 1.2 (adapter foundation)
Task 2.1 → Task 2.2 (config setup)
Tasks 1.2, 2.2 → Task 3.1 (core mocking)
Task 3.1 → Task 3.2 (caching)
Tasks 3.2, 4.1 → Task 4.2 (integration)
```

## 📊 Success Criteria

- ✅ 8 atomic tasks (≤2h each) following TDD principles
- ✅ 95%+ unit test coverage with MockK
- ✅ Zero dependency on real HTTP calls or UI tests
- ✅ Clean Architecture compliance with existing patterns
- ✅ Compatible with Ktor 2.3.+ and existing OkHttp integration
- ✅ Shared cache functionality validated across both clients

## 📦 추가 의존성

**build.gradle.kts**에 테스트용 의존성 추가:
```kotlin
implementation("io.ktor:ktor-client-core:2.3.+")
testImplementation("io.ktor:ktor-client-mock:2.3.+") // for testing
```