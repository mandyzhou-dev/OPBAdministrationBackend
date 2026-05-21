# 一期认证鉴权接入实施 Checklist Plan

日期：2026-05-17

相关目录：

- Auth service 来源：`/Users/marktwain/Projects/brinavi/brinavi`
- Backend：`/Users/marktwain/Projects/OPBAdministrationBackend`
- Frontend：`/Users/marktwain/Projects/OPBOA`

本版 plan 的目标不是继续评估，而是说明现有代码库应该做什么。核心路径是：先把 brinavi 中已有认证/鉴权能力抽离成可修改、可独立运行的 auth service，并让它基于 OPB 现有 `opb_user` 表完成认证和权限输出；再让 OPBAdministrationBackend 作为资源服务接入 token 验证；最后让 OPBOA 前端切到独立 auth service 登录并携带 token 调用 OPB backend。

## 0. 一期硬边界

一期必须满足：

- 不修改任何现有表结构。
- 不新增表，不迁移数据，不改字段，不改约束。
- brinavi 抽离出的鉴权代码可以修改，且主要修改对象就是这个独立 auth service。
- auth service 必须独立运行，不嵌入 OPBAdministrationBackend 进程。
- OPBAdministrationBackend 只作为业务资源服务接入 auth service 输出的 token。
- 保留 OPBAdministrationBackend 现有 `/api/user/login` 等旧入口的兼容窗口，不做 breaking change。
- 暂不做审计、refresh token 持久化、token rotation、token 黑名单、注册/验证码/重置密码迁移。

一期完成后应该能证明：

- brinavi-derived auth service 能连接 OPB 数据库，读取 `opb_user`，用 OPB 现有账号密码完成登录。
- auth service 能返回 OPBOA 可消费的用户摘要和 access token。
- OPBAdministrationBackend 能验证该 access token，并基于 OPB 用户身份/角色保护至少一组代表性业务 API。
- OPBOA 能通过 auth service 登录，并带 token 调用 OPBAdministrationBackend。

## 1. 目标架构

```text
OPBOA
  -> POST /auth/api/login                独立 auth service 登录
  <- accessToken + OPB user summary

OPBOA
  -> Authorization: Bearer <token>
  -> OPBAdministrationBackend /api/...

OPBAdministrationBackend
  -> 本地验证 JWT 签名和 claims
  -> 建立当前用户上下文
  -> 根据角色/登录态保护业务 API

brinavi-derived auth service
  -> 读取 OPBAdministrationBackend 当前使用的 MySQL schema
  -> 只读或最小必要读取 opb_user
  -> 不改 OPB 表结构
```

服务职责：

- brinavi-derived auth service：登录认证、密码校验、OPB 用户字段映射、JWT 签发、`/me` 当前用户摘要、健康检查。
- OPBAdministrationBackend：业务 API、token 验证、用户上下文、角色鉴权、兼容旧登录入口。
- OPBOA：登录入口切换、认证状态管理、请求头注入、401/403 处理、手机端登录/会话失效体验。

## 2. 推荐完成顺序与任务成果

### 2.1 brinavi 抽离和独立运行

应该在现有代码库做什么：

- 从 `/Users/marktwain/Projects/brinavi/brinavi` 中以 `brinavi-identity`、`brinavi-security`、`brinavi-shared` 为起点，抽离或复制出一个独立 auth service。
- 保留它作为独立 Spring Boot 服务的启动入口、配置文件、Maven module 或独立工程边界。
- 删除或隔离与 OPB 一期无关的 brinavi 业务模块依赖，例如 task、taskprofile、scheduler、collector、tenant-context、Kafka 等。
- 保留并可修改 JWT、登录、密码校验、用户模型映射相关代码。
- 明确服务端口、context path、配置文件位置、数据库连接配置、JWT secret 配置。

产出成果：

- 一个可独立启动的 brinavi-derived auth service。
- 服务启动不依赖 OPBAdministrationBackend 同进程运行。
- 至少具备 `GET /health` 或 Spring Actuator health 类似健康检查。
- Maven 构建范围清楚，能单独 build auth service。

验收方式：

- 在 auth service 目录单独运行 Maven build 成功。
- 单独启动 auth service 后，健康检查返回成功。
- 关闭 OPBAdministrationBackend 时，auth service 仍能启动并响应健康检查。
- 文档中能明确说明 auth service 的启动命令、端口和必需配置项。

### 2.2 auth service 改造为 OPB 用户表适配

应该在现有代码库做什么：

- 将 brinavi identity 原有用户实体改造成 OPB 用户适配实体，映射当前 `opb_user` 表。
- 适配 OPB 后端当前 `UserDO` 字段：`username`、`name`、`password`、`roles`、`email`、`phoneNumber`、`address`、`birthdate`、`active`、`groupName`、`bigDay`。
- 登录 ID 兼容 OPB 现状：`LoginDTO.username` 当前可接受 username 或 email，因此 auth service 登录也应支持 username/email。
- 密码校验兼容 OPB 当前密码编码方式。OPB 后端当前 Spring Security 使用 `DelegatingPasswordEncoder` + bcrypt；auth service 需要先验证现有 `opb_user.password` 的实际格式，再选择复用 brinavi `PasswordService` 或改为 Spring `PasswordEncoder`。
- `active` 必须进入认证规则：非 active 用户不能登录，或明确返回认证失败。
- `roles` 继续兼容 OPB 现有字符串格式，不要求改表；如存在 pipe 分隔或单值字符串，需要在 auth service 输出时标准化为数组。

产出成果：

- auth service 拥有 OPB 专用用户 JPA entity/repository/service。
- auth service 可基于 OPB `opb_user` 完成 username 登录、email 登录、密码校验、active 校验。
- auth service 输出统一用户摘要：

```json
{
  "username": "employee1",
  "name": "Employee One",
  "roles": ["tester"],
  "email": "employee@example.com",
  "active": 1,
  "groupName": "A"
}
```

验收方式：

- 使用 OPB 现有数据库中的测试用户，通过 auth service username/password 登录成功。
- 使用同一用户 email/password 登录成功。
- 错误密码返回 401 或明确登录失败响应。
- inactive 用户不能登录。
- 登录响应不包含 password、sinno 等敏感字段。
- 没有任何 SQL migration 或表结构变更。

### 2.3 auth service API 契约落地

应该在现有代码库做什么：

- 在 auth service 中实现一期最小 API。
- 登录 API 返回 access token 和用户摘要，兼容 OPBOA 当前登录后保存用户对象的使用方式。
- 当前用户 API 用 Bearer token 返回用户摘要。
- token 验证 API 可选：如果 OPBAdministrationBackend 采用本地 JWT 验证，`/introspect` 可暂不作为强依赖；如果后端希望服务间校验，则实现该 API。
- 退出 API 一期只返回成功，由 OPBOA 清本地状态；不做服务端 token 撤销。

一期建议 API：

| API | 调用方 | 目的 | 一期成果 |
| --- | --- | --- | --- |
| `POST /auth/api/login` | OPBOA | username/email + password 登录 | 返回 `accessToken`、`tokenType`、`expiresIn`、`user` |
| `GET /auth/api/me` | OPBOA | 根据 token 恢复当前用户 | 返回用户摘要 |
| `POST /auth/api/logout` | OPBOA | 前端退出 | 返回 204/200，前端清本地状态 |
| `POST /auth/api/introspect` | OPB backend 可选 | 服务间 token 校验 | 返回 active、username、roles、groupName |
| `GET /auth/health` | 部署/联调 | 健康检查 | 返回服务可用 |

产出成果：

- auth service API contract 文档。
- DTO 命名清楚，例如 `OpbLoginRequest`、`OpbLoginResponse`、`OpbUserSummary`、`TokenIntrospectionResponse`。
- 统一错误语义：登录失败 401，token 缺失/无效 401，权限不足由资源服务返回 403。

验收方式：

- curl/Postman 可调用 login 并取得 token。
- token 可调用 `/auth/api/me` 取回同一用户摘要。
- token 过期或篡改时 `/me` 返回 401。
- API 响应字段能直接支持 OPBOA 当前 `User` 模型扩展，不要求页面大改。

### 2.4 auth service JWT claims 设计

应该在现有代码库做什么：

- 修改 brinavi `JwtTokenService` 或等价实现，使 token claims 能承载 OPB 后端鉴权所需的最小信息。
- token subject 使用 `username`。
- claims 至少包含 `roles`、`active`、`groupName`，可包含 `name`、`email`。
- JWT secret、issuer、TTL 通过配置提供，OPBAdministrationBackend 使用同一 secret 或公钥配置验证。
- 不把敏感信息放入 token。

建议 claims：

```json
{
  "sub": "employee1",
  "roles": ["tester"],
  "active": 1,
  "groupName": "A",
  "name": "Employee One",
  "email": "employee@example.com",
  "iss": "opb-auth-service",
  "exp": 1234567890
}
```

产出成果：

- auth service 能签发 OPB backend 可验证的 JWT。
- claims 字段与 OPB backend 资源服务过滤器约定一致。
- token TTL 明确，建议一期沿用 OPB 当前约 10 小时 access token 兼容窗口。

验收方式：

- 解析 token 可看到 `sub`、`roles`、`active`、`groupName`。
- OPBAdministrationBackend 使用相同配置能验证签名和过期时间。
- roles 从 OPB 当前字符串稳定转换为后端可判断的 authority 列表。

### 2.5 OPBAdministrationBackend 接入为资源服务

应该在现有代码库做什么：

- 在 `/Users/marktwain/Projects/OPBAdministrationBackend` 中新增或改造资源服务侧 JWT filter，不再只依赖当前 `UserController.login` 生成的 token。
- 复用或扩展 `ca.openbox.infrastructure.jwt.JwtUtil`，使其能解析 auth service token 的 claims。
- 修改 `ca.openbox.user.configuration.SecurityConfiguration`：保留公开入口，同时对选定代表性业务 API 加入认证要求。
- 将 token 中的 username、roles、active、groupName 建立为 Spring Security 当前用户上下文。
- 将 OPB 当前 `roles` 字符串映射为 `GrantedAuthority`，用于后续 `@PreAuthorize` 或 request matcher。
- 旧 `/api/user/login` 保留，避免前端切换期间 breaking。

一期建议先保护的 API：

- 选择一组读 API 和一组写 API 做代表性验证，例如：
  - `GET /api/presentor/user/employees/basic`
  - `GET /api/presentor/shift/{username}/findVisibleShifts`
  - `PUT /api/shift/shiftarrangement`
- 具体 API 可由 Backend_Dev 根据风险选择，但不能一次性把所有 endpoint 强制鉴权。

产出成果：

- OPBAdministrationBackend 可以验证 auth service token。
- 无 token 访问受保护 API 返回 401。
- token 有效但角色不足返回 403。
- 未纳入一期保护的 API 保持旧行为。
- 后端配置中明确 `auth.jwt.secret` 或等价配置来源。

验收方式：

- 不带 token 调用受保护 API，返回 401。
- 携带 auth service 登录得到的 token 调用受保护 API，返回正常业务响应。
- 使用 tester 等低权限角色访问 manager-only API，返回 403。
- 旧 `/api/user/login` 仍可用，旧流程不被立即破坏。
- Maven test 或至少后端启动验证通过。

### 2.6 OPBOA 前端接入 auth service

应该在现有代码库做什么：

- 在 `/Users/marktwain/Projects/OPBOA` 中新增 auth service base URL 配置，例如 `EXPO_PUBLIC_AUTH_API_URL`，保留现有 `EXPO_PUBLIC_API_URL` 指向 OPB backend。
- 将 `service/UserService.ts`、`request/UserRequest.ts` 的登录路径从 OPB backend 旧 `/api/user/login` 切到 auth service `POST /auth/api/login`，或新增 `AuthRequest/AuthService` 后让登录页调用新服务。
- 扩展 `model/User.ts` 或新增 `AuthSession` TypeScript interface，明确 `accessToken`、`tokenType`、`expiresIn`、`user`。
- 统一 axios 请求拦截器：调用 OPB backend 业务 API 时自动加 `Authorization: Bearer <accessToken>`。
- 统一 401/403 处理：401 清登录态并回登录页；403 展示无权限状态，不做无限跳转。
- 当前 `localStorage.setItem("user", JSON.stringify(data))` 需要改为保存 session 对象，且兼容移动端存储限制；Expo 移动端不能只依赖 Web localStorage，应确认是否使用 AsyncStorage/SecureStore 或当前项目已有存储封装。
- 手机端适配：登录页、会话失效提示、无权限页面在小屏不遮挡、不空白、不死循环。

产出成果：

- 前端有清晰的 `AuthSession` 类型。
- 登录成功后保存 access token 和用户摘要。
- 所有业务请求自动携带 token。
- 前端能区分 auth service URL 和 OPB backend URL。
- 移动端登录/退出/会话失效路径明确。

验收方式：

- Web 端登录后刷新页面仍能恢复登录态或按预期回登录。
- 登录后访问排班/申请/团队等页面，业务请求带 `Authorization` header。
- token 缺失或过期时跳回登录态，不出现空白页或无限跳转。
- 403 时展示无权限提示，页面布局在手机宽度下可用。
- 注册、验证码、重置密码等仍走 OPB backend 旧接口，一期不被 auth service 改造阻断。

### 2.7 前后端联调顺序

应该怎么做：

1. 只启动 auth service，验证 OPB 用户表登录。
2. 启动 OPBAdministrationBackend，配置为信任 auth service token。
3. 用 auth service token 直接调用 OPB 受保护 API。
4. 启动 OPBOA，登录走 auth service，业务 API 走 OPB backend。
5. 逐步增加受保护 API 范围。

产出成果：

- 联调记录包含每一步请求、响应、失败定位方式。
- 明确三个服务各自配置：
  - auth service DB/JWT/port
  - OPB backend JWT/permit list/protected API list
  - OPBOA auth URL/backend URL

验收方式：

- auth service 独立登录成功。
- OPB backend token 校验成功。
- OPBOA 端到端登录并访问至少一个受保护业务页面成功。
- 回退时只需把前端登录入口切回旧 `/api/user/login`，OPB backend 保留旧兼容入口。

## 3. 前后端交互契约

### 3.1 Login

`POST /auth/api/login`

Request:

```json
{
  "username": "employee1-or-email@example.com",
  "password": "plain-password"
}
```

Response:

```json
{
  "tokenType": "Bearer",
  "accessToken": "jwt",
  "expiresIn": 36000,
  "user": {
    "username": "employee1",
    "name": "Employee One",
    "roles": ["tester"],
    "email": "employee@example.com",
    "active": 1,
    "groupName": "A"
  }
}
```

### 3.2 Current User

`GET /auth/api/me`

Header:

```text
Authorization: Bearer <accessToken>
```

Response:

```json
{
  "username": "employee1",
  "name": "Employee One",
  "roles": ["tester"],
  "email": "employee@example.com",
  "active": 1,
  "groupName": "A"
}
```

### 3.3 OPB Backend Business API

Header:

```text
Authorization: Bearer <accessToken>
```

Error semantics:

- 401：未登录、token 缺失、token 无效、token 过期。
- 403：已登录但角色不足。
- 5xx：服务异常，不应被前端当作登录失效处理。

## 4. 数据模型与类型边界

### 4.1 auth service Java DTO

建议 DTO：

- `OpbLoginRequest`
  - `String username`
  - `String password`
- `OpbLoginResponse`
  - `String tokenType`
  - `String accessToken`
  - `long expiresIn`
  - `OpbUserSummary user`
- `OpbUserSummary`
  - `String username`
  - `String name`
  - `List<String> roles`
  - `String email`
  - `Integer active`
  - `String groupName`
- `TokenIntrospectionResponse`（可选）
  - `boolean active`
  - `String username`
  - `List<String> roles`
  - `String groupName`

### 4.2 auth service JPA entity

基于现有 OPB 表，只做映射，不改表：

- table：`opb_user`
- id：`username`
- fields：与 OPB `UserDO` 对齐。

### 4.3 OPBOA TypeScript interface

建议新增或调整：

```ts
export interface AuthUser {
  username: string;
  name: string;
  roles: string[];
  email?: string;
  active?: number;
  groupName?: string;
}

export interface AuthSession {
  tokenType: "Bearer";
  accessToken: string;
  expiresIn: number;
  user: AuthUser;
}
```

兼容注意：

- OPBOA 当前 `User.roles` 是可选 string；新 auth session 建议使用 `string[]`，UI 层如果仍依赖字符串，需要提供兼容 helper。
- 一期不要让前端角色判断成为安全边界；后端必须做最终鉴权。

## 5. 任务分工建议

### Backend_Dev / auth service

- 从 brinavi 抽离独立 auth service。
- 改造 OPB 用户表映射。
- 实现 login/me/logout/introspect 可选 API。
- 实现 OPB JWT claims。
- 提供 auth service API contract 和启动配置说明。

### Backend_Dev / OPBAdministrationBackend

- 接入 auth service JWT 验证。
- 改造 SecurityConfiguration 的分阶段保护规则。
- 建立用户上下文和 roles authority 映射。
- 给代表性 API 加 401/403 验证。
- 保留旧登录兼容入口。

### Frontend_Dev / OPBOA

- 新增 auth service URL 配置。
- 新增 AuthSession/AuthUser 类型。
- 登录切到 auth service。
- axios 请求统一注入 Bearer token。
- 统一 401/403 处理。
- 检查 Web 和手机端登录、退出、会话失效体验。

## 6. 一期验收 Checklist

- [ ] auth service 可从 brinavi-derived 代码独立 build。
- [ ] auth service 可独立启动并返回 health。
- [ ] auth service 可连接 OPB 数据库，不改任何表。
- [ ] auth service 可用 OPB username/password 登录。
- [ ] auth service 可用 OPB email/password 登录。
- [ ] auth service 对 inactive 用户拒绝登录。
- [ ] auth service 登录响应包含 access token 和用户摘要，不包含敏感字段。
- [ ] token claims 包含 OPB backend 需要的 username、roles、active、groupName。
- [ ] OPBAdministrationBackend 可验证 auth service token。
- [ ] 至少一个 OPB 业务 API 无 token 返回 401。
- [ ] 至少一个 OPB 业务 API 角色不足返回 403。
- [ ] OPBOA 登录走 auth service。
- [ ] OPBOA 业务请求自动带 Bearer token。
- [ ] OPBOA 401/403 行为明确，手机端不出现空白页或无限跳转。
- [ ] 旧 `/api/user/login` 保留兼容，注册/验证码/重置密码不受影响。

## 7. 暂不做事项

- 不新增 refresh token 表。
- 不新增审计表。
- 不做服务端 logout token 黑名单。
- 不迁移 OPB 用户注册、验证码、重置密码。
- 不一次性收紧所有 OPB backend endpoint。
- 不把 brinavi 作为 jar 直接塞进 OPBAdministrationBackend 同进程使用。

## 8. 开放问题

需要用户或团队确认：

- brinavi-derived auth service 放在 brinavi repo 内新 module，还是复制到独立目录/仓库运行。
- auth service 一期部署端口和外部访问路径，例如 `/auth/api` 是否固定。
- OPB 当前生产密码是否全部为 Spring bcrypt/DelegatingPasswordEncoder 可验证格式。
- OPB 现有 `roles` 字段是否存在多角色分隔格式；如果有，分隔符以当前数据为准。
- 一期代表性受保护 API 选择哪几组，建议从用户列表、排班读接口、排班写接口各选一个。
- OPBOA 移动端是否已有可用安全存储封装；如果没有，一期至少要明确 Web localStorage 与移动端存储的差异处理。
