# TypeScript & JavaScript Testing & TDD Slot Implementation

> **Layer**: `testing`  
> **Implements**: `.ai/guidelines/testing/META.md`  
> **Target**: TypeScript 5.x / Node.js 20+ / Jest or Vitest

---

## 1. CANONICAL_STUB
```typescript
throw new Error("Not implemented: ClassName.methodName");
```

## 2. FORBIDDEN_SILENT_STUBS
- `return null;`
- `return undefined;`
- `return 0;`
- `return false;`
- `return "";`
- `return [];`
- `return {};`
- `{}` (empty function body)

## 3. TEST_FRAMEWORK_AND_MOCK_LIB
- **Runner**: Jest or Vitest
- **Mock Library**: Built-in `jest.fn()` or `vi.fn()`

## 4. MOCK_PRECONFIGURATION_SYNTAX
```typescript
mockCollaborator.execute.mockResolvedValue(validResult);
// Or synchronous:
mockCollaborator.execute.mockReturnValue(validResult);
```

## 5. MOCK_BEHAVIOR_VERIFICATION_SYNTAX
```typescript
expect(mockCollaborator.execute).toHaveBeenCalledWith(expectedArguments);
```

## 6. STUB_SCAN_PATTERNS
- `throw new Error("Not implemented")`

## 7. QUALITY_GATE_TASK_SNIPPET
```json
// In package.json "scripts"
{
  "scripts": {
    "check-no-stubs": "node -e 'const glob=require(\"glob\"),fs=require(\"fs\");let v=[];glob.sync(\"src/**/*.{ts,js}\").forEach(f=>fs.readFileSync(f,\"utf8\").split(\"\\n\").forEach((l,i)=>{if(l.includes(\"Not implemented\"))v.push(`${f}:${i+1}`)}));if(v.length){console.error(\"checkNoStubs FAILED:\\n\"+v.join(\"\\n\"));process.exit(1)}else{console.log(\"checkNoStubs passed.\")}'"
  }
}
```
