# Kotlin Testing & TDD Slot Implementation

> **Layer**: `testing`  
> **Implements**: `.ai/guidelines/testing/META.md`  
> **Target**: Kotlin 2.x / JVM 21 / MockK & Mockito-Kotlin

---

## 1. CANONICAL_STUB
```kotlin
throw UnsupportedOperationException("Not implemented: ClassName.methodName")
```

## 2. FORBIDDEN_SILENT_STUBS
- `return null`
- `return 0`
- `return false`
- `return ""`
- `return emptyList()`
- `return emptySet()`
- `return emptyMap()`
- `{}` (empty function body, including Unit-returning functions)

## 3. TEST_FRAMEWORK_AND_MOCK_LIB
- **Runner**: JUnit 5 (`org.junit.jupiter.api.*`)
- **Mock Library**: MockK (`io.mockk.*`) or Mockito-Kotlin (`org.mockito.kotlin.*`)

## 4. MOCK_PRECONFIGURATION_SYNTAX
```kotlin
// MockK:
every { mockCollaborator.execute(any()) } returns validResult

// Mockito-Kotlin:
whenever(mockCollaborator.execute(any())).thenReturn(validResult)
```

## 5. MOCK_BEHAVIOR_VERIFICATION_SYNTAX
```kotlin
// MockK:
verify(exactly = 1) { mockCollaborator.execute(expectedArguments) }

// Mockito-Kotlin:
verify(mockCollaborator, times(1)).execute(expectedArguments)
```

## 6. STUB_SCAN_PATTERNS
- `UnsupportedOperationException`
- `throw RuntimeException("Not implemented")`
- `TODO("Not implemented")`
- `NotImplementedError`

## 7. QUALITY_GATE_TASK_SNIPPET
```kotlin
// In build.gradle.kts (Gradle Kotlin DSL)
tasks.register("checkNoStubs") {
    group = "verification"
    description = "Fails the build if any Not-Implemented stubs remain in src/main."
    doLast {
        val stubPatterns = listOf("UnsupportedOperationException", "Not implemented", "TODO")
        val mainSrc = fileTree("src/main") { include("**/*.java", "**/*.kt") }
        val violations = mainSrc.files.flatMap { file ->
            file.readLines().mapIndexedNotNull { idx, line ->
                stubPatterns.firstOrNull { line.contains(it) }
                    ?.let { "${file.relativeTo(projectDir)}:${idx + 1} → stub: $it" }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException("checkNoStubs FAILED:\n" + violations.joinToString("\n"))
        }
        logger.lifecycle("checkNoStubs passed — zero stub violations in src/main.")
    }
}
tasks.named("test") { dependsOn("checkNoStubs") }
```
