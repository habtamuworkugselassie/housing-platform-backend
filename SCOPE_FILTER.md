# Scope-Based Authorization Filter

This document explains the scope-based authorization filter system implemented for the Housing Platform Backend.

## Overview

The `ScopeAuthorizationFilter` provides fine-grained access control based on:
- **Policy Scopes**: Portal-level access (BUYER_SECURED, BANKER_SECURED, etc.)
- **Action Scopes**: Specific action permissions (e.g., "properties.create", "loans.approve")

## Annotations

### @AuthPolicyScope

Defines the security policy for an endpoint:

```java
@AuthPolicyScope(AuthPolicyScope.Policy.BUYER_SECURED)
```

**Available Policies:**
- `UNSECURED` - No authentication required
- `AUTHENTICATED` - Any authenticated user
- `BUYER_SECURED` - Requires buyer scope
- `BANKER_SECURED` - Requires banker scope
- `REALTOR_SECURED` - Requires realtor scope
- `SUPPLIER_SECURED` - Requires supplier scope
- `ADMIN_SECURED` - Requires admin scope

### @AuthActionScope

Defines specific action permissions:

```java
@AuthActionScope("properties.create")
```

Examples:
- `properties.read`
- `properties.create`
- `properties.update`
- `properties.delete`
- `loans.approve`
- `loans.reject`
- `payments.process`

## Usage Examples

### Class-Level Annotation

```java
@RestController
@RequestMapping("/api/v1/properties")
@AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
public class PropertyController {
    // All methods require authentication
}
```

### Method-Level Annotation

```java
@PostMapping
@AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
@AuthActionScope("properties.create")
public ResponseEntity<PropertyResponse> createProperty(...) {
    // Requires realtor scope + properties.create action
}
```

### Override Class Annotation

```java
@RestController
@AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED) // Default for all methods
public class PropertyController {
    
    @GetMapping
    // Uses class-level AUTHENTICATED policy
    
    @PostMapping
    @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED) // Overrides class-level
    @AuthActionScope("properties.create")
    public ResponseEntity<PropertyResponse> createProperty(...) {
        // Requires REALTOR_SECURED policy
    }
}
```

## JWT Token Structure

The JWT token must include scopes in the payload:

```json
{
  "sub": "user-uuid",
  "email": "user@example.com",
  "scope": "buyer properties.read properties.create",
  "roles": ["BUYER"],
  "iss": "housing-platform",
  "exp": 1234567890
}
```

The `scope` claim should contain:
1. Portal scope (buyer, banker, realtor, supplier, admin)
2. Action scopes (properties.create, loans.approve, etc.)

## How It Works

1. **Request comes in** → Filter intercepts
2. **Extract annotations** → From method and class
3. **Determine required scopes** → From `@AuthPolicyScope` and `@AuthActionScope`
4. **Extract token scopes** → From JWT token `scope` claim
5. **Validate** → Check if token has required scopes
6. **Allow/Deny** → Grant or deny access

## Scope Validation Logic

- Token must have **at least one** of the required scopes
- Admin scope (`admin`) has access to everything
- Policy scopes are mapped to portal scopes:
  - `BUYER_SECURED` → requires `buyer` scope
  - `BANKER_SECURED` → requires `banker` scope
  - etc.

## Custom JWT Authentication Token

The system uses `HousingPlatformJwtAuthenticationToken` which extends `JwtAuthenticationToken` and provides:

```java
HousingPlatformJwtAuthenticationToken token = (HousingPlatformJwtAuthenticationToken) authentication;

// Get scopes
Set<String> scopes = token.getScopes();

// Check scope
boolean hasScope = token.hasScope("buyer");

// Get user info
String userId = token.getUserId();
String email = token.getEmail();
```

## Example: Complete Controller

```java
@RestController
@RequestMapping("/api/v1/properties")
@AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
public class PropertyController {
    
    @GetMapping
    @AuthActionScope("properties.read")
    public ResponseEntity<Page<PropertyResponse>> getAllProperties(...) {
        // Requires: authenticated + properties.read scope
    }
    
    @PostMapping
    @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
    @AuthActionScope("properties.create")
    public ResponseEntity<PropertyResponse> createProperty(...) {
        // Requires: realtor scope + properties.create scope
    }
    
    @PutMapping("/{id}")
    @AuthPolicyScope(AuthPolicyScope.Policy.REALTOR_SECURED)
    @AuthActionScope("properties.update")
    public ResponseEntity<PropertyResponse> updateProperty(...) {
        // Requires: realtor scope + properties.update scope
    }
}
```

## Error Responses

### Missing Scope

```json
{
  "timestamp": "2024-01-01T12:00:00",
  "status": 403,
  "error": "Access Denied",
  "message": "Access denied. Required scopes: [realtor, properties.create], but token has: [buyer]",
  "path": "/api/v1/properties"
}
```

### Unauthenticated

```json
{
  "timestamp": "2024-01-01T12:00:00",
  "status": 403,
  "error": "Access Denied",
  "message": "Authentication required",
  "path": "/api/v1/properties"
}
```

## Best Practices

1. **Always annotate endpoints** - Use `@AuthPolicyScope` and `@AuthActionScope`
2. **Use class-level for defaults** - Set default policy at class level
3. **Override at method level** - More restrictive policies at method level
4. **Include scopes in tokens** - Ensure login endpoint includes all necessary scopes
5. **Document action scopes** - Maintain a list of all action scopes used

## Action Scope Naming Convention

Use dot notation: `<resource>.<action>`

Examples:
- `properties.read`
- `properties.create`
- `properties.update`
- `properties.delete`
- `loans.approve`
- `loans.reject`
- `payments.process`
- `users.manage`

## Integration with Login

When generating tokens in `AuthenticationService`, include action scopes:

```java
List<String> scopes = new ArrayList<>();
scopes.add(portalScope); // buyer, banker, etc.
scopes.add("properties.read"); // Add action scopes based on user permissions
scopes.add("properties.create");

String token = jwtTokenProvider.generateToken(userId, email, scopes, roles);
```
