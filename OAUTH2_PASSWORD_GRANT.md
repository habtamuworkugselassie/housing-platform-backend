# OAuth2 Password Grant Type

This document describes the OAuth2 Password Grant Type implementation for the Housing Platform Backend.

## Overview

The platform supports OAuth2 Password Grant Type (RFC 6749 Section 4.3) for obtaining access tokens using username and password credentials.

## Endpoint

**POST** `/oauth2/token`

## Request Format

The request must be sent as `application/x-www-form-urlencoded`.

### Required Parameters

- `grant_type`: Must be `password`
- `username`: User's email, phone number, or username
- `password`: User's password

### Optional Parameters

- `scope`: Space-separated list of scopes (e.g., `buyer properties.read`)
  - If not provided, all user's available scopes will be included
  - If provided, only scopes available to the user will be granted

## Response Format

### Success Response (200 OK)

```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "scope": "buyer properties.read"
}
```

### Error Response (400 Bad Request)

```json
{
  "error": "invalid_grant",
  "error_description": "Invalid credentials"
}
```

## Usage Examples

### Basic Request

```bash
curl -X POST http://localhost:8080/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "username=user@example.com" \
  -d "password=password123"
```

### Request with Scopes

```bash
curl -X POST http://localhost:8080/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "username=user@example.com" \
  -d "password=password123" \
  -d "scope=buyer properties.read properties.create"
```

### Using the Access Token

```bash
# Get token
RESPONSE=$(curl -X POST http://localhost:8080/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "username=user@example.com" \
  -d "password=password123")

# Extract access token
ACCESS_TOKEN=$(echo $RESPONSE | jq -r '.access_token')

# Use token in API calls
curl -X GET http://localhost:8080/api/v1/properties \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

## Error Codes

| Error Code | Description |
|------------|-------------|
| `invalid_request` | Missing required parameter (username, password, or grant_type) |
| `invalid_grant` | Invalid credentials or user account not active |
| `invalid_scope` | Requested scope is not available to the user |
| `server_error` | Internal server error during token generation |

## Scope Validation

- If no scope is requested, all user's available scopes are included
- If scope is requested, only scopes available to the user are granted
- Admin users always have access to all scopes
- Invalid scopes result in `invalid_scope` error

## Available Scopes

### Portal Scopes
- `buyer` - Buyer Portal access
- `banker` - Banker Portal access
- `realtor` - Real Estate Portal access
- `supplier` - Supplier Portal access
- `admin` - Admin Portal access

### Action Scopes (Examples)
- `properties.read` - Read properties
- `properties.create` - Create properties
- `properties.update` - Update properties
- `properties.delete` - Delete properties
- `loans.approve` - Approve loans
- `loans.reject` - Reject loans
- `credit-products.create` - Create credit products
- `financing-offers.create` - Create financing offers

## Security Considerations

1. **HTTPS Required**: Always use HTTPS in production
2. **Token Storage**: Store tokens securely on the client
3. **Token Expiration**: Access tokens expire in 1 hour (configurable)
4. **Refresh Tokens**: Use refresh tokens to obtain new access tokens
5. **Scope Limitation**: Request only necessary scopes

## Comparison with /api/v1/auth/login

| Feature | `/api/v1/auth/login` | `/oauth2/token` |
|---------|---------------------|-----------------|
| Standard | Custom | OAuth2 RFC 6749 |
| Request Format | JSON | Form URL-encoded |
| Response Format | Custom JSON | OAuth2 Standard |
| Scope Request | No | Yes |
| Use Case | Simple login | OAuth2 clients |

## Integration Example (JavaScript)

```javascript
// OAuth2 Password Grant
async function getToken(username, password, scope) {
  const formData = new URLSearchParams();
  formData.append('grant_type', 'password');
  formData.append('username', username);
  formData.append('password', password);
  if (scope) {
    formData.append('scope', scope);
  }
  
  const response = await fetch('http://localhost:8080/oauth2/token', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded'
    },
    body: formData
  });
  
  if (!response.ok) {
    throw new Error('Authentication failed');
  }
  
  return await response.json();
}

// Use token
const tokenData = await getToken('user@example.com', 'password123', 'buyer properties.read');
localStorage.setItem('accessToken', tokenData.access_token);
localStorage.setItem('refreshToken', tokenData.refresh_token);
```

## Testing

You can test the OAuth2 password grant using:

1. **cURL** (as shown in examples above)
2. **Postman**: 
   - Method: POST
   - URL: `http://localhost:8080/oauth2/token`
   - Body: x-www-form-urlencoded
   - Parameters: grant_type, username, password, scope
3. **Swagger UI**: The endpoint is documented in Swagger

## Notes

- The password grant type is suitable for trusted clients
- For untrusted clients, consider using Authorization Code grant
- Tokens include scopes in the JWT payload
- The endpoint is public (no authentication required for the token request itself)
