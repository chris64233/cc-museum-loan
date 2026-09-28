# cc-museum-loan

管理馆藏品、保护条件和机构借展资料，支持借展机构一次申请多件馆藏品，并在保护条件满足后**原子批准整组借展**。

## 开发环境

- JDK 21
- Spring Boot 4.1.1
- Maven Wrapper 3.9.9
- H2（JPA + 事务 + 数据库约束）

## 本地运行

启动服务：

    ./mvnw spring-boot:run

运行测试：

    ./mvnw clean test

## 领域模型

- **馆藏品（Artifact）**：唯一藏品号 `catalogNo`、是否可外借 `loanable`、允许温度区间
  `[minTemperature, maxTemperature]`、允许相对湿度区间 `[minHumidity, maxHumidity]`、
  自身运输风险等级 `transportRisk`。
- **借展机构（BorrowingInstitution）**：唯一编号 `code`、可承担的最高风险等级 `maxRisk`。
- **借展申请（LoanRequest）**：机构、多件不重复藏品（申请行 `LoanRequestItem`，
  数据库唯一约束 `(loan_request_id, artifact_id)` 兜底）、借展起止日期（闭区间）、
  承诺温湿度区间、运输方案风险等级、状态、拒绝/取消原因。
- **审计记录（AuditRecord）**：提交、批准、拒绝、取消各追加一条不可变记录（动作、
  前后状态、原因、时间）。
- **幂等记录（IdempotencyRecord）**：`(operation, idempotency_key)` 唯一约束，
  保存首次请求的内容哈希与成功响应。

风险等级 `LOW < MEDIUM < HIGH`，等级高的覆盖等级低的。

## 主要业务规则

### 申请提交（POST /api/loan-requests）

- 申请内容完整校验：机构与全部藏品必须存在；藏品号不得重复；起止日期、承诺温湿度
  各自构成合法区间（下限 ≤ 上限，湿度 0–100）。
- 校验通过后保存为 **待审批（PENDING）**；待审批申请不占用任何藏品。

### 整组原子批准（POST /api/loan-requests/{requestNo}/approve）

批准时在**单个事务**内对每件藏品重新检查：

1. 藏品当前可外借；
2. 机构风险能力足够：机构 `maxRisk` 同时覆盖藏品运输风险与本次运输方案风险；
3. 承诺温湿度区间**完全落在**藏品允许范围内（闭区间比较）；
4. 该藏品不存在**日期重叠的已批准借展**。日期区间为闭区间：
   `existing.start <= candidate.end && existing.end >= candidate.start`，
   因此**同一天交接也算冲突**（前一借展结束日 = 后一借展开始日即重叠）。

任一藏品不满足任一条件 → 抛出 409，**整组失败、事务回滚，不预留任何藏品**；
全部满足 → 整组置为 **已批准（APPROVED）** 并写入审计记录。

**并发控制**：批准时对申请行加悲观写锁，再按 id 升序对全部目标藏品行加悲观写锁
（`PESSIMISTIC_WRITE`，统一加锁顺序避免死锁）。两个申请并发争抢任一相同藏品时，
后到事务在藏品锁上等待，获得锁后重新做重叠检查并整组失败 —— **最多一个成功**。
藏品占用完全由“已批准且日期重叠”的申请推导，无额外预留表，取消即自动释放。

### 拒绝与取消

- **拒绝**（POST .../reject）：仅待审批申请可拒绝，必须填写原因；记录原因与审计。
- **取消**（POST .../cancel）：仅**尚未开始**（起始日期晚于今天）的已批准借展可取消，
  必须填写原因；取消后状态为 CANCELLED，全部藏品随之释放（同区间可再次获批）。
  已开始或已结束的借展不可取消。

### 幂等

所有状态操作（提交/批准/拒绝/取消）要求携带 `Idempotency-Key` 请求头：

- 相同键 + 相同请求内容 → **重放首次成功结果**，不重复执行（提交重放返回 200 与原申请，
  首次为 201）；
- 相同键 + 不同内容 → **409 冲突**；
- 内容一致性按请求规范化哈希（SHA-256）判定；幂等记录与业务变更同事务提交；
- 并发同键由数据库唯一约束兜底，后到者回滚并提示重试；
- 仅记录成功结果，失败操作未改变状态，重放会确定性再次失败。

### 查询

- `GET /api/loan-requests/{requestNo}`：申请详情（藏品行 + 审计时间线）。
- `GET /api/loan-requests/calendar/{catalogNo}?from=&to=`：单件藏品借展日历，
  列出已批准借展的占用区间，可按日期范围过滤（闭区间重叠）。

## API 一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | /api/artifacts | 登记馆藏品 |
| GET | /api/artifacts, /api/artifacts/{catalogNo} | 查询藏品 |
| POST | /api/institutions | 登记借展机构 |
| GET | /api/institutions, /api/institutions/{code} | 查询机构 |
| POST | /api/loan-requests | 提交借展申请（需 Idempotency-Key） |
| POST | /api/loan-requests/{requestNo}/approve | 整组原子批准（需 Idempotency-Key） |
| POST | /api/loan-requests/{requestNo}/reject | 拒绝（需 Idempotency-Key + 原因） |
| POST | /api/loan-requests/{requestNo}/cancel | 取消（需 Idempotency-Key + 原因） |
| GET | /api/loan-requests/{requestNo} | 申请详情 |
| GET | /api/loan-requests/calendar/{catalogNo} | 藏品借展日历 |

错误响应统一为 `{status, error, code, message, details[], timestamp}`；
校验失败 400、资源不存在 404、业务冲突（重叠/幂等冲突/非法状态流转）409。

## 测试

`LoanServiceTest`（19 例）覆盖提交校验、批准四类条件、同日交接冲突、整组原子性、
并发争抢最多一个成功、幂等重放/冲突、拒绝取消与日历；`LoanRequestApiTest`（6 例）
覆盖 REST 契约、幂等键头行为与错误响应。
