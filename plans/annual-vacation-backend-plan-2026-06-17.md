# Backend Plan: Annual Vacation

日期：2026-06-17

项目目录：`/Users/marktwain/Projects/OPBAdministrationBackend`

当前阶段只写后端方案，不写业务代码，不执行 SQL，不直接操作数据库。

## 1. 目标口径

- 新增 leave application 类别：`ANNUAL_VACATION`。
- 新增 shift status：`annual_vacation`，由 HR/Manager 在已有 schedule cell 上标记。
- 员工连续受雇满 12 个月后才可申请 annual vacation；未满 12 个月完全不能申请。
- 员工申请阶段只做基础余额校验：没有有效额度记录、未满 12 个月、剩余天数为 0 时阻止；只要剩余至少 1 天，不按日期跨度推算消耗天数，也不要求当前 schedule 已存在。
- 实际扣减发生在 HR/Manager mark schedule cell 为 `annual_vacation` 时；按 distinct `America/Vancouver` business date 计 1 天，参考 paid sick leave 口径。
- 不允许半天/部分小时。申请 annual vacation 时使用完整日期；实际 non-worked hours 使用被标记 shift 的原始 scheduled hours，不使用固定 8 小时。
- 到期清零，不 carry over。新的周年周期由 HR 在数据库维护新额度记录。
- vacation pay 不进系统。

## 2. 现有系统依据

相关模块：

- Leave application：`ca.openbox.process`
- Shift status / paid sick leave：`ca.openbox.shift`
- Work statistics：`ca.openbox.statistics`
- User/employment anniversary：`opb_user.big_day` / `UserDO.bigDay`

关键现状：

- `LeaveApplicationService.validateLeaveApplicationDates(...)` 已有 leaveType 分支：`SICK` 会要求每个日期有 shift；普通 leave 只禁止过去日期。
- `ShiftArrangementService.updateStatus(...)` 是现有 HR/Manager 标记 shift status 的入口。
- `ShiftStatus` enum 当前有 `active`、`cancelled`、`no_show`、`paid_sick_leave`、`unpaid_sick_leave`，并集中定义 manual target 与 non-worked status。
- `paid_sick_leave` quota 按 Vancouver local year + distinct date 计算，`annual_vacation` 应沿用“按天去重”的实现方式，但周期不是 calendar year，而是服务周年周期。
- `WorkTimeStatisticsPresentor` 和 `WorkLoadCalculator` 通过 `ShiftStatus.isNonWorked(...)` 排除 worked hours / KPI 输入；新增 `annual_vacation` 后必须纳入 non-worked。
- `ShiftPresentationRepository` 的 native SQL status allowlist 需要加入新状态，否则 schedule 和统计读不到被标记的 annual vacation shift。

## 3. 数据建模建议

不要放在 `application_variables`：

- `application_variables` 适合全局配置，如 KPI rate、probation months，不适合每个员工每个服务年度的额度。

不要只放在 `opb_user` profile 字段：

- 年假额度按服务年度刷新，用户表单字段无法自然表达“哪一年额度、周期起止、HR 当时维护的 regular workdays/entitlement”。

推荐新增表：`opb_annual_vacation_entitlement`

- 一行代表一个员工一个服务年度的 annual vacation 额度。
- `opb_user.big_day` 仍作为入职/周年日来源。
- HR 维护 `regular_workdays_per_week` 与 `entitlement_days`；系统不自动复杂推算。
- `usedDays` 不单独存储，运行时从 `opb_shift_arrangement.status = 'annual_vacation'` 的 distinct Vancouver business date 计算，避免 HR 手动余额与实际 schedule 标记不一致。

## 4. 数据库 SQL

由用户执行，开发 agent 不直接运行。

```sql
CREATE TABLE IF NOT EXISTS opb_annual_vacation_entitlement (
  id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(100) NOT NULL,
  service_year_start DATE NOT NULL,
  service_year_end DATE NOT NULL,
  regular_workdays_per_week DECIMAL(4,2) NULL,
  entitlement_days INT NOT NULL,
  note TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_annual_vacation_entitlement_user
    FOREIGN KEY (username) REFERENCES opb_user(username),
  CONSTRAINT uq_annual_vacation_entitlement_user_year
    UNIQUE (username, service_year_start),
  INDEX idx_annual_vacation_entitlement_username_dates
    (username, service_year_start, service_year_end)
);
```

可选 MySQL 8.0.16+ check constraints：

```sql
ALTER TABLE opb_annual_vacation_entitlement
  ADD CONSTRAINT chk_annual_vacation_entitlement_days
  CHECK (entitlement_days >= 0),
  ADD CONSTRAINT chk_annual_vacation_entitlement_dates
  CHECK (service_year_end >= service_year_start);
```

HR 维护示例：

```sql
INSERT INTO opb_annual_vacation_entitlement
  (username, service_year_start, service_year_end, regular_workdays_per_week, entitlement_days, note)
VALUES
  ('employee_username', '2026-07-01', '2027-06-30', 3.00, 6, 'Year 2 entitlement based on previous service year');

UPDATE opb_annual_vacation_entitlement
SET regular_workdays_per_week = 5.00,
    entitlement_days = 10,
    note = 'Adjusted by HR'
WHERE username = 'employee_username'
  AND service_year_start = '2026-07-01';
```

不建议给 `opb_shift_arrangement.status` 加 SQL enum/check constraint，因为现有生产口径是普通 `varchar(32)`，代码层集中校验更符合当前项目模式。

## 5. 后端类型与常量

新增/修改：

- `ShiftStatus`: 增加 `ANNUAL_VACATION("annual_vacation", true, true)`。
- `LeaveApplicationService`: 增加 `ANNUAL_VACATION_LEAVE_TYPE = "ANNUAL_VACATION"`。
- 新增 `AnnualVacationEntitlementDO` 映射 `opb_annual_vacation_entitlement`。
- 新增 `AnnualVacationEntitlementRepository`。
- 新增 `AnnualVacationBalanceDTO`。
- 新增 `AnnualVacationService`，集中处理 eligibility、active entitlement、used days、remaining days。

推荐 DTO：

```json
{
  "username": "employee1",
  "businessZone": "America/Vancouver",
  "eligible": true,
  "eligibilityReason": "ELIGIBLE",
  "serviceYearStart": "2026-07-01",
  "serviceYearEnd": "2027-06-30",
  "regularWorkdaysPerWeek": 3.0,
  "entitlementDays": 6,
  "usedDays": 2,
  "remainingDays": 4
}
```

`eligibilityReason` 建议值：

- `ELIGIBLE`
- `MISSING_BIG_DAY`
- `BEFORE_FIRST_ANNIVERSARY`
- `NO_ENTITLEMENT_RECORD`
- `NO_REMAINING_DAYS`

## 6. API 设计

### 6.1 员工申请前余额查询

```text
GET /api/process/application/annual-vacation-balance?applicant=<username>
```

用途：

- 前端 Annual Vacation 表单显示额度。
- 提交前做 basic balance guard。

返回 `AnnualVacationBalanceDTO`。

### 6.2 员工提交 annual vacation application

复用现有：

```text
PUT /api/process/application/leave-application
```

Request 中 `leaveType` 使用：

```json
{
  "applicant": "employee1",
  "start": "2026-08-01T00:00:00-07:00",
  "end": "2026-08-05T23:59:00-07:00",
  "leaveType": "ANNUAL_VACATION",
  "reason": "Annual vacation"
}
```

后端校验：

- `start/end` 必填。
- Vancouver business date 不可早于 today。
- `start <= end`。
- full-day semantics：annual vacation 不接受任意 `HHmm-HHmm` 的部分小时申请；允许 one-day 或 range，但时间应规范为 start day `00:00`，end day `23:59` 或后端定义的 full-day boundary。
- 不检查 selected dates 是否有 shift。
- 不按申请日期跨度与 remainingDays 比较。
- 只检查 active entitlement 存在且 `remainingDays > 0`。

### 6.3 HR 标记 annual vacation shift

复用并扩展：

```text
PATCH /api/shift/shiftarrangement/{id}/status
```

Request：

```json
{
  "status": "annual_vacation",
  "operatorUsername": "manager_username"
}
```

后端校验：

- operator 必须 manager，沿用 `assertManager`。
- shift 必须存在。
- status 必须在 manual target 内。
- 当目标 status 是 `annual_vacation`：
  - 找到 shift username 的 active service-year entitlement。
  - target shift date 必须落在该 entitlement period 内。
  - 计算该 period 内已标记 `annual_vacation` 的 distinct Vancouver dates。
  - 如果 target date 已经被 counted，允许保存，不额外消耗天数。
  - 如果 target date 未 counted 且 `usedDays >= entitlementDays`，阻止。
  - 如果未满 12 个月、无 entitlement record、remaining 0，阻止。

### 6.4 HR 标记前 quota 查询

```text
GET /api/shift/shiftarrangement/{id}/annual-vacation-quota?operatorUsername=<manager>
```

返回同 `AnnualVacationBalanceDTO`，并加：

```json
{
  "targetDate": "2026-08-01",
  "targetDateAlreadyCounted": false,
  "canMarkAnnualVacation": true,
  "message": "Annual vacation used: 2/6"
}
```

前端用它禁用/提示 `Mark as annual vacation`。

## 7. Service 设计

新增 `AnnualVacationService`：

- `getCurrentBalance(String username, LocalDate asOfDate)`
- `getQuotaForShift(ShiftArrangementDO shift)`
- `assertCanSubmitApplication(String username)`
- `assertCanMarkShift(ShiftArrangementDO shift)`
- `countUsedDays(String username, LocalDate serviceYearStart, LocalDate serviceYearEnd)`

周期选择：

- 使用 `opb_user.big_day` 判断是否满 12 个月。
- active entitlement 以 `asOfDate` 命中 `service_year_start <= asOfDate <= service_year_end` 为准。
- 申请阶段 `asOfDate = today in America/Vancouver`。
- HR mark 阶段 `asOfDate = target shift Vancouver local date`，这样跨周年日的 shift 使用对应服务年度额度。

Used days 计算：

- 查询 `opb_shift_arrangement` 中该 username、`status = 'annual_vacation'`、`start` 在 service period day range 内的 shifts。
- 转换 `start` 到 `America/Vancouver` local date。
- distinct local date 后计数。
- 同一天多个 annual vacation shifts 只消耗 1 天。

## 8. 影响点

后端文件：

- `src/main/java/ca/openbox/shift/entities/ShiftStatus.java`
- `src/main/java/ca/openbox/shift/service/ShiftArrangementService.java`
- `src/main/java/ca/openbox/shift/controller/ShiftArrangementController.java`
- `src/main/java/ca/openbox/shift/repository/ShiftArrangementRepository.java`
- `src/main/java/ca/openbox/shift/repository/ShiftPresentationRepository.java`
- `src/main/java/ca/openbox/process/service/LeaveApplicationService.java`
- `src/main/java/ca/openbox/process/controller/LeaveApplicationController.java`
- 新增 `src/main/java/ca/openbox/process/dataobject/AnnualVacationEntitlementDO.java`
- 新增 `src/main/java/ca/openbox/process/repository/AnnualVacationEntitlementRepository.java`
- 新增 `src/main/java/ca/openbox/process/dto/AnnualVacationBalanceDTO.java`
- 新增 `src/main/java/ca/openbox/process/service/AnnualVacationService.java`
- `src/main/java/ca/openbox/statistics/presentor/WorkTimeStatisticsPresentor.java`
- `src/main/java/ca/openbox/statistics/service/WorkLoadCalculator.java`

注意：`WorkTimeStatisticsPresentor` 当前直接跳过 non-worked status；加入 `annual_vacation` 后，annual vacation 不进入 worked hours。若未来需要“non-worked hours by type”报表，应新增独立统计，不要把它混进 worked hours。

## 9. 边界情况

- `bigDay == null`：不可申请，不可 mark annual vacation。
- 已满 12 个月但 HR 没有建 entitlement row：不可申请，不可 mark，返回 `NO_ENTITLEMENT_RECORD`。
- entitlementDays = 0：不可申请，不可 mark 新日期。
- 员工申请 range 很长但 remainingDays > 0：允许提交。
- 员工申请时对应日期没有 shift：允许提交。
- HR mark 时不存在 schedule cell：不会发生，因为 API 基于 shift id；如果 shift id 不存在则 404。
- HR mark 同一 Vancouver local date 多个 shifts：只扣 1 天；如果这一天已 counted，允许再次标记。
- HR mark 跨服务年度：按 target shift local date 找对应 entitlement period。
- 将已有 annual vacation shift 改成其他 status 会释放一天吗：如果该日没有其他 annual_vacation shifts，运行时 usedDays 会自然减少；这与“不单独存 usedDays”保持一致。
- 被标记 annual vacation 的 shift 保留原 start/end，所以 non-worked hours 能按原 scheduled hours 计算。

## 10. 后端开发任务拆分

1. 建表 SQL 由用户执行，开发前确认表存在。
2. 添加 `AnnualVacationEntitlementDO`、repository、DTO。
3. 添加 `AnnualVacationService`，先写 service unit tests 覆盖 eligibility、period lookup、used day distinct counting、remaining 0。
4. 扩展 `LeaveApplicationService`：`ANNUAL_VACATION` 分支只校验 full-day、past date、remaining > 0，不检查 schedule，不按跨度扣减。
5. 扩展 `LeaveApplicationController`：新增 balance query endpoint。
6. 扩展 `ShiftStatus`：加入 `annual_vacation` manual target + non-worked。
7. 扩展 `ShiftArrangementService.updateStatus`：目标 status 为 annual vacation 时调用 `AnnualVacationService.assertCanMarkShift(...)`。
8. 新增 shift quota endpoint 与 DTO。
9. 更新 `ShiftPresentationRepository` native SQL status allowlist，加入 `annual_vacation`。
10. 更新统计/KPI相关测试，确认 `annual_vacation` 不进入 worked hours/KPI input。
11. 更新 CORS/security allowlist 与 preflight tests，如新增 GET/PATCH endpoint 受影响。

## 11. 后端验证建议

Focused Maven tests：

```bash
mvn test -Dtest=LeaveApplicationServiceDateAvailabilityTest,ShiftArrangementServiceTest,WorkLoadCalculatorTest,LeaveApplicationControllerAvailabilityTest
```

新增测试建议：

- `AnnualVacationServiceTest`
- `LeaveApplicationServiceAnnualVacationTest`
- `ShiftArrangementServiceAnnualVacationTest`
- `ShiftArrangementControllerAnnualVacationCorsTest`
- `WorkTimeStatisticsAnnualVacationTest`

