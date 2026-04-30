# Login Email Support Plan

## Goal

Update the existing login flow so `POST /api/user/login` accepts either a username or an email address while preserving the current request and response shape.

The current request body uses a field named `username`. To avoid a frontend-breaking API change, this plan treats that field as a login identifier. The canonical identity remains the persisted username, and JWT subjects should continue to use the username.

Status: implemented.

## Current Login Logic

The login workflow is documented in `.codex/skills/opb-login-workflow/SKILL.md`.

Key locations:

- `src/main/java/ca/openbox/user/controller/UserController.java`: `login(...)`
- `src/main/java/ca/openbox/user/dto/LoginDTO.java`: request body
- `src/main/java/ca/openbox/user/dto/UserDTO.java`: response body
- `src/main/java/ca/openbox/user/configuration/SecurityConfiguration.java`: authentication manager and password encoder
- `src/main/java/ca/openbox/user/service/UserService.java`: `UserDetailsService` and user lookup
- `src/main/java/ca/openbox/user/repository/UserRepository.java`: user persistence queries
- `src/main/java/ca/openbox/infrastructure/jwt/JwtUtil.java`: JWT generation

## Implementation Steps

1. Add an active-user email lookup to `UserRepository`. Completed.

   Preferred minimal method:

   ```java
   UserDO getUserDOByEmailAndActiveIsTrue(String email);
   ```

2. Add a resolver method to `UserService`. Completed.

   Suggested method:

   ```java
   public UserDO getActiveUserDOByUsernameOrEmail(String identifier) {
       UserDO userDO = userRepository.getUserDOByUsernameAndActiveIsTrue(identifier);
       if (userDO != null) return userDO;
       return userRepository.getUserDOByEmailAndActiveIsTrue(identifier);
   }
   ```

   Also add a domain wrapper if controller code needs a `User`:

   ```java
   public User getUserByUsernameOrEmail(String identifier) {
       UserDO userDO = getActiveUserDOByUsernameOrEmail(identifier);
       return userDO == null ? null : User.fromDO(userDO);
   }
   ```

3. Update `UserService.loadUserByUsername(...)`. Completed.

   Spring Security passes the submitted login field into this method. After the change, that input may be either username or email.

   Required behavior:

   - Resolve active user by username first.
   - If no username match exists, resolve by email.
   - If neither exists, throw `UsernameNotFoundException`.
   - Build `UserDetails` using `userDO.getUsername()` as the canonical principal and `userDO.getPassword()` as the stored encoded password.

4. Update `UserController.login(...)`. Completed.

   Keep the authentication token creation unchanged:

   ```java
   UsernamePasswordAuthenticationToken.unauthenticated(
       loginDTO.getUsername(),
       loginDTO.getPassword()
   )
   ```

   After authentication succeeds, load the response user with the new resolver:

   ```java
   User user = userService.getUserByUsernameOrEmail(loginDTO.getUsername());
   ```

5. Keep `LoginDTO` backward-compatible. Completed.

   Do not rename `username` yet unless the frontend is ready for a breaking change. If clearer naming is desired later, add a new `identifier` field in a versioned API or support both fields temporarily.

6. Keep JWT behavior unchanged. Completed.

   `JwtUtil.generateToken(user)` should continue to set the token subject to `user.getUsername()`. This keeps downstream identity canonical even when the user logged in with email.

7. Decide whether to update `/api/user/verify_password`. Completed.

   It now uses the same username-or-email resolver for consistency, because its request also includes `username` and `password`.

8. Update documentation. Completed.

   - Update `.codex/skills/opb-login-workflow/SKILL.md` to state that `LoginDTO.username` accepts either username or email.
   - Link the OpenAPI document from `Readme.md`.
   - Keep `docs/openapi-login.yaml` aligned with the implemented response shape.

9. Add tests when a test structure exists. Pending because this repository currently has no `src/test` sources.

   Suggested cases:

   - Active user logs in successfully with username.
   - Active user logs in successfully with email.
   - Wrong password fails for username.
   - Wrong password fails for email.
   - Inactive user cannot log in by username.
   - Inactive user cannot log in by email.
   - Login response returns canonical `username`.
   - JWT subject is canonical username, not email.

## Acceptance Criteria

- Existing username login still works without frontend changes.
- Email login works through the same `POST /api/user/login` endpoint.
- Login response remains `UserDTO`.
- `token` is still generated.
- JWT subject remains canonical username.
- Inactive accounts cannot log in by either username or email.
- README and OpenAPI documentation describe the username-or-email identifier behavior.
