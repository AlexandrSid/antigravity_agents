# Java Testing & TDD Slot Implementation

> **Layer**: `testing`  
> **Implements**: `.ai/guidelines/testing/META.md`  
> **Target**: Java 21 LTS / JUnit 5 / Mockito

---

## 1. CANONICAL_STUB
```java
throw new UnsupportedOperationException("Not implemented: ClassName.methodName");
```

## 2. FORBIDDEN_SILENT_STUBS
- `return null;`
- `return 0;`
- `return false;`
- `return "";`
- `return Collections.emptyList();`
- `return List.of();`
- `return Optional.empty();`
- `// TODO` with fallback return
- `{}` (empty method body, including void methods)

## 3. TEST_FRAMEWORK_AND_MOCK_LIB
- **Runner**: JUnit 5 (`org.junit.jupiter.api.*`)
- **Mock Library**: Mockito (`org.mockito.Mockito.*`)

## 4. MOCK_PRECONFIGURATION_SYNTAX
```java
when(mockCollaborator.execute(any())).thenReturn(validResult);
// Or:
doReturn(validResult).when(mockCollaborator).execute(any());
```

## 5. MOCK_BEHAVIOR_VERIFICATION_SYNTAX
```java
verify(mockCollaborator, times(1)).execute(expectedArguments);
```

## 6. STUB_SCAN_PATTERNS
- `UnsupportedOperationException`
- `throw new RuntimeException("Not implemented")`

## 7. QUALITY_GATE_TASK_SNIPPET
```kotlin
// In build.gradle.kts (Gradle Kotlin DSL)
tasks.register("checkNoStubs") {
    group = "verification"
    description = "Fails the build if any Not-Implemented stubs remain in src/main."
    doLast {
        val stubPatterns = listOf("UnsupportedOperationException", "Not implemented")
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
