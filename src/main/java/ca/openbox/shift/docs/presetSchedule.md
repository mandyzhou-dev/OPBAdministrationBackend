# Schedule Preset Feature – Technical Design

## 1. Overview

This feature allows managers to quickly generate the next week's schedule by copying shifts from the current week.

The goal is to reduce repetitive manual scheduling when weekly schedules follow a consistent pattern.

The system provides a **Preset Next Week** action that copies shifts from the current week to the next week.

The operation must support:

* conflict handling
* partial success reporting

---

## 2. Functional Requirements

### 2.1 Preset Next Week

Managers can trigger an action to copy the schedule for a **selected group** from:

source week → target week

Example:

Source week:
2026-03-01 → 2026-03-07

Target week:
2026-03-08 → 2026-03-14

All shifts **within the selected group** from the source week will be duplicated to the target week.

The time offset is calculated as:

offsetDays = targetWeekStart − sourceWeekStart

---

### 2.2 Conflict Handling

Two conflict handling modes are supported:

| Mode                | Behavior                                                          |
| ------------------- | ----------------------------------------------------------------- |
| SKIP_CONFLICTS      | Existing shifts remain unchanged. Conflicting shifts are skipped. |
| OVERWRITE_CONFLICTS | Existing conflicting shifts will be deleted and replaced.         |

---

### 2.3 Conflict Definition

A conflict occurs when the generated shift is assigned to an employee who already has a shift on the same day.

Conflict rule:

same employee  
AND same shift date  
→ conflict
---


## 3. API Specification

### Endpoint

POST /api/shift/preset

---

### 3.1 Request

---

#### Request Body

```json
{
  "groupName": "string",
  "sourceWeekStart": "YYYY-MM-DD",
  "targetWeekStart": "YYYY-MM-DD",
  "mode": "SKIP_CONFLICTS | OVERWRITE_CONFLICTS"
}
```

#### Field Definitions

| Field           | Type   | Required | Description               |
|-----------------|--------| -------- | ------------------------- |
| groupName       | string | yes      | Schedule group to copy |
| sourceWeekStart | date   | yes      | Start date of source week |
| targetWeekStart | date   | yes      | Start date of target week |
| mode            | enum   | yes      | Conflict handling mode    |

---

#### Example Request

```json
{
  "groupName": "coquitlam",
  "sourceWeekStart": "2026-03-01",
  "targetWeekStart": "2026-03-08",
  "mode": "SKIP_CONFLICTS"
}
```

---

### 3.2 Response

#### Success Response

HTTP 200

```json
{
  "created": 22,
  "skipped": 2,
  "overwritten": 0,
  "conflicts": [
    {
      "username": "mandy",
      "reason": "EMPLOYEE_ALREADY_SCHEDULED_ON_DATE",
      "existingShiftId": "2545",
      "requestedDate": "2026-03-09"
    },
    {
      "username": "mandy",
      "reason": "EMPLOYEE_ALREADY_SCHEDULED_ON_DATE",
      "existingShiftId": "2546",
      "requestedDate":"2026-03-10"
    }
  ]
}
```

#### Response Fields

| Field       | Description                   |
| ----------- | ----------------------------- |
| created     | Number of new shifts created  |
| skipped     | Number of skipped shifts      |
| overwritten | Number of overwritten shifts  |
| conflicts   | List of conflicts encountered |

#### Conflict Object
| Field           | Type              | Description                                               |
| --------------- | ----------------- | --------------------------------------------------------- |
| username        | string            | Employee username                                         |
| reason          | enum              | Reason for the conflict                                   |
| existingShiftId | string            | ID of the existing shift that caused the conflict         |
| requestedDate   | date (YYYY-MM-DD) | Shift date in **store timezone** (e.g. America/Vancouver) |

---

## 4. Backend Design (Java)

### 4.1 Service Flow

Preset execution steps:

1. Validate request (sourceWeekStart, targetWeekStart, mode)
2. Load source week shifts
3. Calculate time offset:
   offsetDays = targetWeekStart - sourceWeekStart
4. Generate candidate shifts by applying offsetDays to each source shift
5. Detect conflicts (day-level, based on store(coquitlam) timezone)
6. Apply conflict strategy (SKIP_CONFLICTS / OVERWRITE_CONFLICTS)
7. Insert shifts
8. Return summary result

### 4.2 DTOs

#### PresetRequest
- groupName: string
- sourceWeekStart: string (ISO 8601 date/time)
- targetWeekStart: string (ISO 8601 date/time)
- mode: enum { SKIP_CONFLICTS, OVERWRITE_CONFLICTS }

#### PresetResult
- created: number
- skipped: number
- overwritten: number
- conflicts: ConflictItem[]

#### ConflictItem
- username: string
- reason: enum { EMPLOYEE_ALREADY_SCHEDULED_ON_DATE }
- existingShiftId: string
- requestedDate: string (YYYY-MM-DD, store timezone)

---

## 5. Database Constraints

No new database table is required. This feature reads and writes the existing shift_arrangement table.

Duplicate-prevention constraints are not required for this scope because:

- In SKIP_CONFLICTS, repeated executions naturally result in conflicts and are skipped.

- In OVERWRITE_CONFLICTS, existing shifts on the same day are deleted before inserting the new shift.

---

## 6. Frontend Implementation (React)

### UI

Add a button on the Schedule page:

**Copy This Week To…**

---

### Button Flow

User clicks button  
↓  
Open copy dialog  
↓  
User selects target group
↓  
User selects target week and conflict mode  
↓  
Call preset API  
↓  
Show result  
↓  
Reload schedule

---

### Copy Dialog

Copy Schedule

[ Select group ▼ ]  *(default: Surrey)*
Source Week (current page):  
Mar 8 – Mar 14

Target Week:  
[ Select week ▼ ]  *(default: Next week)*

Conflict Handling:

- Skip conflicts
- Overwrite conflicts

[Cancel] [Copy]

---

## 8. Security

Only users with the following roles may execute preset:

Manager

---

## 9. Testing Scenarios

### Scenario 1

No conflicts.

Expected:

created = all shifts;
skipped = 0

---

### Scenario 2

Conflicts + skip mode.

Expected:

conflicting shifts skipped

---

### Scenario 3

Conflicts + overwrite mode.

Expected:

existing shifts deleted; 
new shifts created

---

### Scenario 4

Repeated request.

Expected:

no duplicate shifts

---

## 10. Future Extensions (Not in Scope)

Possible future improvements:

* automatic preset generation
* schedule publish workflow
