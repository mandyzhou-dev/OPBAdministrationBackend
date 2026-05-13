# Backend Plan: Shift Status and Paid Sick Leave Quota

日期：2026-05-13

项目目录：`/Users/marktwain/Projects/OPBAdministrationBackend`

当前阶段只做后端计划，不写业务代码，不操作数据库，不执行 SQL。

## 1. 最终产品口径

用户和 PM 已确认：

- Manager 可把 shift 标记为 3 个目标状态：
  - `no_show`
  - `paid_sick_leave`
  - `unpaid_sick_leave`
- 不加入 `Mark as active / Reset status`。
- 后端状态更新 API 只允许本次 3 个状态变更目标。
- `cancelled` 也从 worked hours 排除。
- KPI 也排除 `no_show` / `paid_sick_leave` / `unpaid_sick_leave` / `cancelled` 这些 non-worked 状态。
- `bigDay == null` 按 probation / not eligible 处理。
- paid sick leave quota 按 America/Vancouver calendar day 计算。
- 同一员工同一天多个 paid sick leave shift 只消耗 1 天 quota。
- 数据库存储或 Java 传输可以是 UTC/global instant；业务日期、quota、statistics、KPI 口径都必须转换到 `America/Vancouver` 后按 local date/year 计算。
- 不依赖数据库 session timezone。
- 生产库 `status` 已确认是普通 `varchar(32)`，无 enum/check constraint。
- 当前不需要 SQL。

## 2. 后端现状

关键文件：

- `src/main/java/ca/openbox/shift/dataobject/ShiftArrangementDO.java`
- `src/main/java/ca/openbox/shift/entities/ShiftArrangement.java`
- `src/main/java/ca/openbox/shift/dto/ShiftArrangementDTO.java`
- `src/main/java/ca/openbox/shift/controller/ShiftArrangementController.java`
- `src/main/java/ca/openbox/shift/service/ShiftArrangementService.java`
- `src/main/java/ca/openbox/shift/repository/ShiftArrangementRepository.java`
- `src/main/java/ca/openbox/shift/presentation/ShiftPresentation.java`
- `src/main/java/ca/openbox/shift/repository/ShiftPresentationRepository.java`
- `src/main/java/ca/openbox/statistics/presentor/WorkTimeStatisticsPresentor.java`
- `src/main/java/ca/openbox/shift/application/KPIApplication.java`
- `src/main/java/ca/openbox/shift/service/KPI/KPICalculator.java`
- `src/main/java/ca/openbox/shift/service/KPI/KPIRecordService.java`
- `src/main/java/ca/openbox/user/service/UserService.java`
- `src/main/java/ca/openbox/user/repository/UserRepository.java`

现状依据：

- `ShiftArrangementDO` 已有 `status` 字段，映射 `opb_shift_arrangement.status`。
- `ShiftArrangementDTO` 已包含 `status`。
- `ShiftArrangement` entity 已能在 DO/DTO 之间传递 status。
- 批量创建 shift 时默认 `status = "active"`。
- `ShiftPresentation` 当前没有 `status` 字段。
- `ShiftPresentationRepository` native query 当前只返回 active/cancelled，且 select 中没有 status。
- `WorkTimeStatisticsPresentor` 当前使用 `shiftPresentationRepository.getByTimeScope()` 计算 worked hours。
- `UserService.isInProbation(username)` 已存在，且 `bigDay == null` 时返回 true，符合最终口径。
- 后端 `shift` 包当前没有 status enum/常量；只存在 String 字段和 repository query 中的字符串条件。

## 3. Status 值设计

继续复用 `opb_shift_arrangement.status`。

状态值：

| 状态值 | 说明 | 是否计入 worked hours | 是否计入 KPI | 是否计入 paid leave quota | 本次 API 是否允许写入 |
| --- | --- | --- | --- | --- | --- |
| `active` | 默认工作状态 | 是 | 是 | 否 | 否 |
| `cancelled` | 现有兼容状态 | 否 | 否 | 否 | 否 |
| `no_show` | 员工未到 | 否 | 否 | 否 | 是 |
| `paid_sick_leave` | 带薪病假 | 否 | 否 | 是，按 calendar day 去重 | 是 |
| `unpaid_sick_leave` | 无薪病假 | 否 | 否 | 否 | 是 |

说明：

- `active` 和 `cancelled` 是兼容/展示/过滤状态。
- 本次状态更新入口只写入 3 个目标状态：`no_show`、`paid_sick_leave`、`unpaid_sick_leave`。
- 不提供 reset/active 写入口。

后端常量/enum 建议：

- 当前代码里没有 shift status enum/常量。
- 建议最小新增 `ca.openbox.shift.entities.ShiftStatus` enum，JPA 仍保存 String。
- enum 提供：
  - `getValue()`
  - `fromValue(String value)`
  - `isAllowedManualTarget()`
  - `isNonWorked()`
- 如果不新增 enum，至少在 `ShiftArrangementService` 中集中定义 `Set<String>`：
  - `MANUAL_STATUS_TARGETS = Set.of("no_show", "paid_sick_leave", "unpaid_sick_leave")`
  - `NON_WORKED_STATUSES = Set.of("cancelled", "no_show", "paid_sick_leave", "unpaid_sick_leave")`
- 推荐 enum，原因是 Statistics、KPI、Schedule projection 和状态更新都会复用同一组状态语义。

## 4. API 设计

### 4.1 状态更新 API

Endpoint：

```text
PATCH /api/shift/shiftarrangement/{id}/status
```

Request：

```json
{
  "status": "no_show",
  "operatorUsername": "manager_username"
}
```

允许 status：

- `no_show`
- `paid_sick_leave`
- `unpaid_sick_leave`

明确不允许：

- `active`
- `cancelled`
- 任意其他字符串

Response：

```json
{
  "id": 123,
  "username": "employee1",
  "start": "2026-05-13T16:30:00Z",
  "end": "2026-05-14T01:00:00Z",
  "status": "paid_sick_leave",
  "groupName": "surrey"
}
```

### 4.2 Paid sick leave quota 查询 API

Endpoint：

```text
GET /api/shift/shiftarrangement/{id}/paid-sick-leave-quota?operatorUsername=manager_username
```

Response：

```json
{
  "username": "employee1",
  "year": 2026,
  "usedDays": 2,
  "quotaDays": 5,
  "probation": false,
  "eligible": true,
  "targetDateAlreadyCounted": false,
  "canMarkPaidSickLeave": true,
  "message": "Used 2/5"
}
```

语义：

- `year` 是目标 shift start 转换到 `America/Vancouver` 后的 local year。
- `usedDays` 是该员工当年 paid sick leave distinct Vancouver local date 数。
- `probation` 复用 `UserService.isInProbation(username)`。
- `eligible = !probation`。
- `targetDateAlreadyCounted` 表示当前 shift start 所在 Vancouver local date 是否已在当年 paid sick leave day set 中。
- `canMarkPaidSickLeave = eligible && (usedDays < 5 || targetDateAlreadyCounted)`。

## 5. DTO 边界

新增 DTO：

`ShiftStatusUpdateDTO`

- `String status`
- `String operatorUsername`

`PaidSickLeaveQuotaDTO`

- `String username`
- `int year`
- `int usedDays`
- `int quotaDays`
- `boolean probation`
- `boolean eligible`
- `boolean targetDateAlreadyCounted`
- `boolean canMarkPaidSickLeave`
- `String message`

## 6. Controller 边界

`ShiftArrangementController` 新增：

```java
@PatchMapping("/{id}/status")
public ShiftArrangement updateStatus(
    @PathVariable Integer id,
    @RequestBody ShiftStatusUpdateDTO dto
)
```

```java
@GetMapping("/{id}/paid-sick-leave-quota")
public PaidSickLeaveQuotaDTO getPaidSickLeaveQuota(
    @PathVariable Integer id,
    @RequestParam String operatorUsername
)
```

Controller 只做：

- 参数接收。
- 调用 `ShiftArrangementService`。
- 返回 DTO/entity。

不在 Controller 中写 quota、权限、timezone、KPI/statistics 规则。

## 7. Service 边界

`ShiftArrangementService` 新增：

```java
public ShiftArrangement updateStatus(Integer shiftId, String newStatus, String operatorUsername)
```

```java
public PaidSickLeaveQuotaDTO getPaidSickLeaveQuota(Integer shiftId, String operatorUsername)
```

内部辅助：

```java
private void assertManager(String operatorUsername)
private void assertManualStatusTarget(String status)
private PaidSickLeaveQuotaDTO buildPaidSickLeaveQuota(ShiftArrangementDO shift)
private Set<LocalDate> getPaidSickLeaveDates(String username, int year, ZoneId zone)
```

状态更新规则：

1. shift 必须存在。
2. operator 必须是 Manager。
3. status 必须是 `no_show`、`paid_sick_leave`、`unpaid_sick_leave` 之一。
4. `no_show` 和 `unpaid_sick_leave` 可直接更新。
5. `paid_sick_leave` 额外校验：
   - 员工不能仍在 probation。
   - quota 未用完，或当前 shift 所在 Vancouver local date 已经计入该员工当年 paid sick leave day set。
6. 保存时只更新 status，避免无意修改 start/end/groupName。

Manager 权限校验：

- 用 `UserRepository` 或 `UserService` 读取 `operatorUsername`。
- 检查 roles 是否包含 `Manager`。
- 当前系统没有强 JWT filter；这是现有架构下的最小后端校验。

异常建议：

- shift 不存在：404 或 400，message `Shift not found`
- 非 Manager：403，message `Only Manager can change shift status`
- invalid status：400
- probation：400，message `Employee is still in probation`
- quota exhausted：400，message `Paid sick leave quota used up`

## 8. Repository 边界

`ShiftArrangementRepository` 当前继承 `JpaRepository<ShiftArrangementDO, Integer>`，已有 `findById`。

建议新增：

```java
List<ShiftArrangementDO> getShiftArrangementDOByUsernameAndStatusAndStartBetween(
    String username,
    String status,
    ZonedDateTime start,
    ZonedDateTime end
);
```

用途：

- 查询某员工某年度内 `paid_sick_leave` shifts。
- 在 Java 中按 Vancouver local date 去重。

不建议用 DB timezone 函数：

- 不依赖 MySQL `CONVERT_TZ`。
- 不依赖数据库 session timezone。
- 业务 timezone 统一在 Java service 里处理。

## 9. Vancouver Timezone 处理

统一规则：

- DB/Java 字段可以保存或传输 UTC/global instant。
- 所有业务 calendar day、calendar year、quota、statistics、KPI 排除口径都以 `America/Vancouver` 为准。
- 在业务计算处显式转换：

```java
ZoneId businessZone = ZoneId.of("America/Vancouver");
LocalDate businessDate = shift.getStart()
    .withZoneSameInstant(businessZone)
    .toLocalDate();
int businessYear = businessDate.getYear();
```

年度范围：

```java
ZonedDateTime yearStart = LocalDate.of(year, 1, 1).atStartOfDay(businessZone);
ZonedDateTime nextYearStart = yearStart.plusYears(1);
```

查询时使用该范围对应的 instant/ZonedDateTime，不在数据库中做 local date 计算。

跨午夜：

- 用户确认当前业务不存在跨午夜 shift。
- 即使未来出现跨午夜，quota 仍按 shift start 转换到 Vancouver 后的 local date 计算。

## 10. Paid Sick Leave Quota 计算

流程：

1. 读取目标 shift。
2. 取 `shift.start` 转 Vancouver local date/year。
3. 调用 `UserService.isInProbation(username)`。
4. 查询该员工该 Vancouver calendar year 内所有 `paid_sick_leave` shifts。
5. 将每条 shift start 转为 Vancouver `LocalDate`。
6. 用 `Set<LocalDate>` 去重。
7. `usedDays = set.size()`。
8. `targetDateAlreadyCounted = set.contains(targetShiftBusinessDate)`。
9. `canMarkPaidSickLeave = !probation && (usedDays < 5 || targetDateAlreadyCounted)`。

`bigDay == null`：

- 复用现有 `UserService.isInProbation`，结果为 true。
- paid sick leave not eligible。

## 11. ShiftPresentation 与 Schedule 可见性

当前问题：

- `ShiftPresentation` 没有 `status`。
- `ShiftPresentationRepository` 查询只返回 active/cancelled。
- 如果直接写入新 status，Schedule 会看不到这些 shifts，员工也无法看到颜色。

计划修改：

1. `ShiftPresentation` 增加 `private String status;`
2. 所有 schedule native select 增加：
   - `opb_shift_arrangement.status as status`
3. schedule 可见状态范围扩展为：
   - `active`
   - `cancelled`
   - `no_show`
   - `paid_sick_leave`
   - `unpaid_sick_leave`

这是读模型调整，不是数据库 schema 调整。

## 12. Statistics worked hours 排除逻辑

最终口径：

- `cancelled`
- `no_show`
- `paid_sick_leave`
- `unpaid_sick_leave`

以上都不计入 worked hours。

计划：

- 在 `ShiftPresentation` 返回 status 后，`WorkTimeStatisticsPresentor` 循环开始处过滤 non-worked statuses。
- 命中则 `continue`。
- 保留现有 lunch deduction 逻辑。
- 如果 statistics 中存在日期分组或未来按天聚合，也必须先把 shift start/end 转换到 Vancouver 业务口径后再处理。

## 13. KPI 排除逻辑

最终口径：

- KPI 也排除 non-worked 状态：
  - `cancelled`
  - `no_show`
  - `paid_sick_leave`
  - `unpaid_sick_leave`

需要核对的后端位置：

- `KPIApplication`
- `KPICalculator`
- `ShiftArrangementService.getByGroupAndDate(...)`
- `ShiftArrangementService.getByUserAndGroupAndDate(...)`
- `WorkLoadCalculator`
- `KPIRecordService` / scheduled KPI refresh 是否间接复用上述计算。

计划：

- 让 KPI 输入 shift list 在 service/repository 层排除 non-worked statuses，避免 calculator 重复判断。
- 如果复用 `ShiftArrangementService.getByGroupAndDate` / `getByUserAndGroupAndDate`，这两个方法应过滤 non-worked statuses，或新增明确的 `getWorkedShifts...` 方法给 KPI/statistics 使用。
- 推荐新增语义清晰的方法，例如：
  - `getWorkedByGroupAndDate(...)`
  - `getWorkedByUserAndGroupAndDate(...)`
- 保留现有方法给需要“所有 visible shifts”的场景，避免影响 Schedule 展示。

## 14. SQL 判断

当前判断：不需要 SQL。

依据：

- 生产库已确认 `status` 是普通 `varchar(32)`。
- 生产库已确认无 enum/check constraint。
- `opb_shift_arrangement.status` 已存在。
- `ShiftArrangementDO.status` 已存在。
- `ShiftArrangementDTO.status` 已存在。
- 新状态值均小于 32：
  - `no_show`
  - `paid_sick_leave`
  - `unpaid_sick_leave`
- quota 可通过现有 shift rows + Java Vancouver local date 去重计算。
- 不需要新表。
- 不需要新字段。
- 不需要新增约束。
- 不需要迁移数据。
- 不需要索引即可完成最小功能。

当前不提供 SQL，不直接操作数据库。

## 15. 后端测试与验证建议

API：

1. Manager 调用 status API，将 active 改为 no_show 成功。
2. Manager 调用 status API，将 active 改为 unpaid_sick_leave 成功。
3. Manager 调用 status API，将 active 改为 paid_sick_leave 成功，前提是 eligible 且 quota 可用。
4. 调用 status API 写 active 被拒绝。
5. 调用 status API 写 cancelled 被拒绝。
6. 非 Manager 调用 status API 被拒绝。
7. invalid status 被拒绝。

Quota：

1. `bigDay == null` 员工 quota response 显示 probation/not eligible。
2. probation 员工 quota response 显示 `probation=true`、`canMarkPaidSickLeave=false`。
3. 非 probation、usedDays 0-4 可标记。
4. usedDays 5 且目标日未计入，不可标记。
5. usedDays 5 且目标日已计入，可标记同一天另一个 shift。
6. 同一天多个 paid sick leave shift 只计 1 天。
7. 年度边界按 Vancouver calendar year。

Schedule projection：

1. no_show shift 仍出现在 Schedule 查询结果中，并包含 `status`。
2. paid_sick_leave shift 仍出现在员工可见查询结果中，并包含 `status`。
3. unpaid_sick_leave shift 仍出现在员工可见查询结果中，并包含 `status`。
4. cancelled shift 如果现有业务需要可见，仍按查询规则返回并包含 status。

Statistics：

1. cancelled shift 不计入 worked minutes。
2. no_show shift 不计入 worked minutes。
3. paid_sick_leave shift 不计入 worked minutes。
4. unpaid_sick_leave shift 不计入 worked minutes。
5. active shift 仍按原 lunch deduction 逻辑计算。

KPI：

1. group daily KPI 排除 non-worked statuses。
2. user daily KPI 排除 non-worked statuses。
3. group biweek KPI 排除 non-worked statuses。
4. user biweek KPI 排除 non-worked statuses。
5. KPI record refresh 如依赖 KPI 计算，也排除 non-worked statuses。

Regression：

1. 批量创建 shift 仍写入 active。
2. 修改 shift 时间/group 不应意外清除 status。
3. 删除 shift 不受影响。

## 16. 风险与剩余问题

风险：

- 当前系统没有强 JWT filter；后端用 `operatorUsername` 校验 Manager 是当前架构下的最小方案，但不是完整安全模型。
- `ShiftPresentationRepository` 是 native query，新增 status 时所有相关 select 都要同步。
- statistics 和 KPI 可能走不同 service path；实现时必须逐个确认非 worked 状态过滤位置。
- `bigDay == null` 会 hardlock paid sick leave，这是最终业务口径。

已确认，不再作为待确认项：

- 不加入 Reset status。
- cancelled 从 worked hours 排除。
- KPI 排除 non-worked 状态。
- `bigDay == null` 按 probation/not eligible。
- 业务日期和 quota 按 America/Vancouver。
- 生产库 status 是普通 varchar(32)，无 enum/check constraint。

剩余必须问用户的问题：

- 无。当前口径足够进入后续实现计划。
