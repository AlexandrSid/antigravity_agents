# TypeScript Tech Stack Slot Implementation

> **Layer**: `languages`  
> **Implements**: `.ai/guidelines/languages/META.md`  
> **Target**: TypeScript 5.x / Node.js 20+ LTS

---

## 1. TARGET_RUNTIME_VERSION
- **Runtime**: Node.js 20+ LTS.
- **Compiler**: TypeScript 5.x.
- **Compiler Options**: `"strict": true`, `"strictNullChecks": true`, `"noImplicitAny": true`.

## 2. IMMUTABLE_DTO_SYNTAX
Use Zod schemas with inferred TypeScript types, or readonly interfaces:
```typescript
import { z } from 'zod';

export const CreateLinkRequestSchema = z.object({
  originalUrl: z.string().url().max(2048),
  customAlias: z.string().max(255).optional(),
  expiresAt: z.string().datetime().optional()
}).readonly();

export type CreateLinkRequest = z.infer<typeof CreateLinkRequestSchema>;
```

## 3. NULLABILITY_SEMANTICS
1. Use `T | undefined` or `.optional()` in schema definitions.
2. In strict null-checking mode, `null` and `undefined` must be handled explicitly.
3. API boundary parsing must fail fast with a 400 Bad Request on schema mismatch.

## 4. LANGUAGE_QUIRKS_AND_KEYWORDS
1. **ES Module Resolution**:
   - Use NodeNext / Bundler module resolution with explicit `.js` extensions when emitting ESM if required by `tsconfig.json`.
2. **Package Locks**:
   - Pinned dependencies via `package-lock.json`, `pnpm-lock.yaml`, or `yarn.lock`.
