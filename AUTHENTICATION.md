# Authentication Guide

This guide explains how to authenticate and get JWT tokens for the Housing Platform Backend.

## Authentication Endpoints

### 1. Login

**Endpoint:** `POST /api/v1/auth/login`

**Request Body:**
```json
{
  "username": "user@example.com",  // Can be email, phone number, or username
  "password": "password123"
}
```

**Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "userId": "123e4567-e89b-12d3-a456-426614174000",
  "email": "user@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "scopes": ["buyer"],
  "roles": ["BUYER"]
}
```

**Example using cURL:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "user@example.com",
    "password": "password123"
  }'
```

### 2. Refresh Token

**Endpoint:** `POST /api/v1/auth/refresh`

**Headers:**
```
Authorization: Bearer <refresh_token>
```

**Response:** Same as login response with new tokens

**Example using cURL:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Authorization: Bearer <refresh_token>"
```

### 3. Logout

**Endpoint:** `POST /api/v1/auth/logout`

**Note:** In a stateless JWT system, logout is handled client-side by discarding tokens. This endpoint is provided for consistency.

## Using the Access Token

Once you have the access token, include it in the `Authorization` header for all protected API calls:

```bash
curl -X GET http://localhost:8080/api/v1/properties \
  -H "Authorization: Bearer <access_token>"
```

## Login Methods

The `username` field in the login request accepts:
- **Email address**: `user@example.com`
- **Phone number**: `+1234567890` or `1234567890`
- **Username**: (if implemented in future)

The system will try to find the user by email first, then by phone number.

## Portal Scopes

The JWT token includes scopes based on the user's roles:

- `buyer` - Buyer Portal access
- `banker` - Banker Portal access
- `realtor` - Real Estate Portal access
- `supplier` - Supplier Portal access
- `admin` - Admin Portal access

## Token Expiration

- **Access Token**: 1 hour (3600 seconds) - configurable via `jwt.expiration`
- **Refresh Token**: 7 days

## Configuration

Set the following environment variables or configure in `application.yml`:

```yaml
jwt:
  secret: ${JWT_SECRET:your-256-bit-secret-key-change-this-in-production-minimum-32-characters-long}
  expiration: ${JWT_EXPIRATION:3600}
  issuer: ${JWT_ISSUER:housing-platform}
```

**Important:** Change the JWT secret in production! Use a strong, random secret key (minimum 32 characters).

## Example: Complete Authentication Flow

```bash
# 1. Login
RESPONSE=$(curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "buyer@example.com",
    "password": "password123"
  }')

# Extract access token
ACCESS_TOKEN=$(echo $RESPONSE | jq -r '.accessToken')

# 2. Use token to access protected API
curl -X GET http://localhost:8080/api/v1/properties \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# 3. When token expires, refresh it
REFRESH_TOKEN=$(echo $RESPONSE | jq -r '.refreshToken')
NEW_RESPONSE=$(curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Authorization: Bearer $REFRESH_TOKEN")
```

## Error Responses

### Invalid Credentials
```json
{
  "timestamp": "2024-01-01T12:00:00",
  "status": 400,
  "error": "Business Rule Violation",
  "message": "Invalid credentials",
  "path": "/api/v1/auth/login"
}
```

### User Not Active
```json
{
  "timestamp": "2024-01-01T12:00:00",
  "status": 400,
  "error": "Business Rule Violation",
  "message": "User account is not active",
  "path": "/api/v1/auth/login"
}
```

### Invalid Refresh Token
```json
{
  "timestamp": "2024-01-01T12:00:00",
  "status": 400,
  "error": "Business Rule Violation",
  "message": "Invalid refresh token",
  "path": "/api/v1/auth/refresh"
}
```

## Security Best Practices

1. **Store tokens securely** - Never store tokens in localStorage for sensitive applications
2. **Use HTTPS** - Always use HTTPS in production
3. **Handle token expiration** - Implement automatic token refresh
4. **Validate tokens** - Always validate tokens on the server side
5. **Rotate secrets** - Regularly rotate JWT secrets
6. **Set appropriate expiration** - Don't set token expiration too long

## Integration with Frontend

### React Example

```javascript
// Login
const login = async (username, password) => {
  const response = await fetch('http://localhost:8080/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  });
  
  const data = await response.json();
  localStorage.setItem('accessToken', data.accessToken);
  localStorage.setItem('refreshToken', data.refreshToken);
  return data;
};

// Use token in API calls
const fetchProperties = async () => {
  const token = localStorage.getItem('accessToken');
  const response = await fetch('http://localhost:8080/api/v1/properties', {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  return response.json();
};
```

## Testing

You can test authentication using Swagger UI:

1. Open http://localhost:8080/swagger-ui.html
2. Navigate to "Authentication" section
3. Try the `/api/v1/auth/login` endpoint
4. Copy the `accessToken` from the response
5. Click "Authorize" button at the top
6. Enter: `Bearer <your_access_token>`
7. Now you can test protected endpoints
