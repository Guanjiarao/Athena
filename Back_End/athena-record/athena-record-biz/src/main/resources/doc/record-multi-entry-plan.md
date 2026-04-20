# record 模块多条同类型同日记录改造实施计划

## 一、改造目标

将 `record` 模块当前“同一用户在同一天对同一记录类型只能保存一条”的实现，改造成：

- 同一 `recordItemId` 在同一天可以保存多条记录
- 每一条记录都以 `id` 作为唯一标识
- 支持对任意一条记录单独修改
- 支持对任意一条记录单独删除
- 旧接口 `/athena/record/save` 直接废弃，不再兼容 upsert 语义

本次改造仅针对日常记录能力，不扩展批量操作、软删除、审计日志等附加能力。

---

## 二、当前实现现状

### 1. 当前查询能力

当前按天查询详情时，已经是返回某一天的全部记录列表：

- 控制器：`DailyRecordController#getDailyDetail`
- 服务层：`RecordServiceImpl#getDailyDetails`

这意味着“同一天返回多条记录”的读取能力基础上已经具备。

### 2. 当前写入限制点

当前问题核心出在 `RecordServiceImpl#saveOrUpdateRecord`：

- 先按 `userId + recordDate + recordItemId` 查询一条记录
- 如果查到：更新或删除
- 如果查不到：插入

这套逻辑天然将“同一类型 + 同一天”视为唯一记录，因此无法新增第二条同类型记录。

### 3. 当前接口语义问题

当前控制器只有一个写接口：

- `POST /athena/record/save`

这个接口同时承担：

- 新增
- 更新
- 删除（通过 `recordValue` 传空触发）

问题包括：

- 语义混乱
- 无法按 `id` 精确定位一条记录
- 一旦支持同类型同天多条，旧接口无法判断要操作哪一条

因此，`/save` 需要直接废弃。

---

## 三、总体改造原则

### 1. 操作对象从“业务键”切换为“主键 id”

从原先的：

- `userId + recordDate + recordItemId`

切换为：

- `id`

### 2. 接口语义拆分

不再使用一个 `/save` 同时承担新增、更新、删除。

改为标准 CRUD 风格：

- 新增：按请求新增一条记录
- 修改：按 `id` 修改一条记录
- 删除：按 `id` 删除一条记录

### 3. 用户边界必须保留

所有更新和删除动作都必须带当前登录用户上下文进行校验，确保用户只能操作自己的记录。

---

## 四、详细实施清单

## 4.1 数据库层改造

### 目标

解除“同一用户 + 同一天 + 同一记录类型只能有一条”的数据库限制。

### 待办

1. 确认 `daily_record` 表当前是否存在唯一索引：
   - `uk_user_date_item`
2. 编写数据库升级脚本
3. 删除唯一索引 `uk_user_date_item`
4. 新增普通索引，建议如下：
   - `idx_user_date(user_id, record_date)`
   - 可选：`idx_user_date_item(user_id, record_date, record_item_id)`

### 验收标准

- 同一个用户、同一天、同一个 `recordItemId` 可以插入多条数据
- 月历打点和按天查询性能不受明显影响

### 文件位置建议

新增 SQL 文件到：

- `athena-record/athena-record-biz/src/main/resources/sql/`

建议文件名：

- `daily_record_multi_entry_upgrade.sql`

---

## 4.2 控制器改造

### 目标

废弃 `/save`，改为清晰的新增、修改、删除接口。

### 当前文件

- `athena-record/athena-record-biz/src/main/java/athena/record/biz/controller/DailyRecordController.java`

### 改造清单

#### 保留接口 1：月打点查询

- `GET /athena/record/marks`

保持不变。

#### 保留接口 2：某天详情查询

- `GET /athena/record/detail`

保持不变，但要确保返回结果中的每条记录都带有 `id`。

#### 废弃接口：

- `POST /athena/record/save`

处理方式建议：

- 直接删除 controller 中的 `/save` 方法
- 不保留兼容逻辑
- 前端同步切换新接口

#### 新增接口 1：新增单条记录

建议接口：

- `POST /athena/record`

请求体建议字段：

- `recordDate`
- `modeType`
- `recordItemId`
- `recordValue`

接口语义：

- 每调用一次就新增一条记录
- 不做按类型覆盖

#### 新增接口 2：修改单条记录

建议接口：

- `PUT /athena/record/{id}`

请求体建议字段：

- `recordValue`
- `modeType`
- 可选：`recordDate`
- 可选：`recordItemId`

建议第一版保守一点，仅开放：

- `recordValue`
- `modeType`

#### 新增接口 3：删除单条记录

建议接口：

- `DELETE /athena/record/{id}`

接口语义：

- 删除当前用户名下指定 `id` 的记录

### 验收标准

- `/save` 已从 controller 移除
- 新增/修改/删除均能通过独立接口完成
- 修改/删除均可精确命中单条记录

---

## 4.3 DTO 设计改造

### 目标

不再直接在 controller 层接收 `DailyRecord` 实体，改用更清晰的 DTO。

### 当前文件

- `athena-record/athena-record-biz/src/main/java/athena/record/biz/domain/dto/RecordDTO.java`

### 当前问题

`RecordDTO` 目前更像半成品：

- 语义不清晰
- 新增和更新共用字段不合适
- 不利于 controller 表达不同接口意图

### 改造建议

废弃单一 `RecordDTO` 方案，拆分为两个 DTO：

#### 1. 新增 DTO

建议新建：

- `CreateDailyRecordDTO`

字段建议：

- `recordDate`
- `modeType`
- `recordItemId`
- `recordValue`

不包含：

- `id`
- `userId`

#### 2. 更新 DTO

建议新建：

- `UpdateDailyRecordDTO`

字段建议：

- `recordValue`
- `modeType`

第一版建议不要开放：

- `recordDate`
- `recordItemId`

这样可以降低前端联调风险，也避免修改后语义变复杂。

### 兼容建议

- 原 `RecordDTO` 如未被其他地方使用，可直接删除
- 如短期不方便删除，可标记废弃并不再引用

### 验收标准

- controller 不再直接接收 `DailyRecord`
- 新增与更新请求体模型分离

---

## 4.4 服务接口改造

### 目标

废弃 upsert 思路，改成清晰的增删改查。

### 当前文件

- `athena-record/athena-record-biz/src/main/java/athena/record/biz/service/RecordService.java`
- `athena-record/athena-record-biz/src/main/java/athena/record/biz/service/RecordServiceImpl.java`

### 改造清单

#### 保留方法

1. `getMonthlyMarks(Long userId, int year, int month)`
2. `getDailyDetails(Long userId, LocalDate date)`

#### 删除方法

- `saveOrUpdateRecord(DailyRecord record)`

#### 新增方法

1. `createRecord(Long userId, CreateDailyRecordDTO dto)`
2. `updateRecord(Long userId, Long id, UpdateDailyRecordDTO dto)`
3. `deleteRecord(Long userId, Long id)`

### 具体实现要求

#### createRecord

逻辑要求：

- 构造 `DailyRecord`
- 设置当前用户 `userId`
- 校验必要字段
- 直接插入
- 不再查询“同类型同天是否已存在”

#### updateRecord

逻辑要求：

1. 根据 `id` 查询记录
2. 校验记录是否存在
3. 校验 `record.userId == currentUserId`
4. 更新允许变更的字段
5. 执行 `updateById`

建议第一版允许更新：

- `recordValue`
- `modeType`

#### deleteRecord

逻辑要求：

1. 根据 `id` 查询记录
2. 校验记录是否存在
3. 校验归属用户
4. 执行删除

### 查询方法补充建议

#### getDailyDetails

建议增加排序，避免同一天返回列表顺序不稳定。

建议：

- 按 `id desc` 排序

### 验收标准

- 服务层不再存在通过 `recordDate + recordItemId` 命中单条记录的更新逻辑
- 所有写操作均围绕 `id` 执行

---

## 4.5 Mapper 层改造

### 目标

尽量少改 mapper，自定义 SQL 只保留必要部分。

### 当前文件

- `athena-record/athena-record-biz/src/main/java/athena/record/biz/domain/mapper/DailyRecordMapper.java`

### 当前情况

目前 mapper 自定义方法只有月打点查询：

- `getRecordedDatesInMonth(...)`

这个方法可以继续保留。

### 改造建议

第一版优先使用 `BaseMapper` 自带能力：

- `selectById`
- `updateById`
- `deleteById`
- `selectList`
- `insert`

因此不强制新增新的 mapper 方法。

### 可选增强

如果后续想更严格控制 SQL，可补充：

- `selectByIdAndUserId`
- `deleteByIdAndUserId`

但本次不是必须。

### 验收标准

- 月打点 SQL 可继续正常使用
- 服务层改造不依赖复杂自定义 SQL

---

## 4.6 实体与返回结构确认

### 目标

保证详情查询结果可以支撑前端对单条记录进行操作。

### 当前文件

- `athena-record/athena-record-biz/src/main/java/athena/record/biz/domain/dataobject/DailyRecord.java`

### 检查项

- `id` 必须出现在查询返回结构中
- `recordDate`
- `modeType`
- `recordItemId`
- `recordValue`

当前实体字段基本已满足本次改造要求。

### 本次建议

- 第一版不强制引入 VO
- 可继续直接返回 `DailyRecord`
- 后续如要做接口规范化，再单独引入 `DailyRecordVO`

### 验收标准

- 前端从 `/detail` 接口中可以拿到每条记录的 `id`
- 可以用该 `id` 发起编辑和删除请求

---

## 4.7 测试补齐

### 目标

确保改造后核心行为稳定。

### 建议新增文件

- `athena-record/athena-record-biz/src/test/java/athena/record/biz/service/RecordServiceImplTest.java`

### 建议测试用例

#### 1. 同一天同类型允许新增多条

测试点：

- 连续新增两条相同 `recordItemId`
- 两次都成功
- 查询某天详情时返回 2 条

#### 2. 按 id 修改其中一条记录

测试点：

- 先造两条同类型同天记录
- 修改第一条的 `recordValue`
- 第二条保持不变

#### 3. 按 id 删除其中一条记录

测试点：

- 删除第一条后
- 第二条仍存在
- 按天详情仍能查到剩余记录

#### 4. 查询某天详情返回顺序稳定

测试点：

- 校验返回结果按预期顺序排序

#### 5. 用户不能修改别人的记录

测试点：

- 当前用户尝试修改不属于自己的记录
- 应抛出异常或返回失败

#### 6. 用户不能删除别人的记录

测试点：

- 当前用户尝试删除不属于自己的记录
- 应抛出异常或返回失败

#### 7. 月打点在同一天多条记录时仍只返回一天

测试点：

- 同一天插入多条记录
- 月打点查询只返回该日期一次

### 验收标准

- 关键场景均有测试覆盖
- 改造后不会因为回归再次变回“同日同类型唯一”

---

## 五、推荐实施顺序

建议按以下顺序执行：

1. 编写数据库升级 SQL，去掉唯一索引
2. 新建 DTO，明确新增与修改入参
3. 修改 `RecordService` 接口定义
4. 修改 `RecordServiceImpl`，移除 `saveOrUpdateRecord`
5. 修改 `DailyRecordController`，删除 `/save`，新增 CRUD 接口
6. 补充测试用例
7. 前端联调，切换为按 `id` 编辑/删除

---

## 六、接口方案建议

### 1. 月打点查询

- `GET /athena/record/marks?year=2026&month=3`

### 2. 某天详情查询

- `GET /athena/record/detail?date=2026-03-11`

### 3. 新增记录

- `POST /athena/record`

请求体示例：

```json
{
  "recordDate": "2026-03-11",
  "modeType": 2,
  "recordItemId": 101,
  "recordValue": "上午一次"
}
```

### 4. 修改记录

- `PUT /athena/record/123`

请求体示例：

```json
{
  "recordValue": "晚上一次",
  "modeType": 2
}
```

### 5. 删除记录

- `DELETE /athena/record/123`

---

## 七、前后端联调注意事项

### 1. 前端必须停止调用 `/save`

由于 `/save` 直接废弃，前端要同步切到新接口：

- 新增走 `POST /athena/record`
- 编辑走 `PUT /athena/record/{id}`
- 删除走 `DELETE /athena/record/{id}`

### 2. 详情页必须保存每条记录的 id

否则无法针对某一条做修改或删除。

### 3. 原先“传空值表示删除”的交互必须移除

删除动作必须走显式删除接口。

---

## 八、本次改造范围边界

### 本次纳入

- 同天同类型多条记录
- 单条新增
- 单条修改
- 单条删除
- `/save` 废弃

### 本次不纳入

- 批量操作
- 软删除
- 审计日志
- 记录附件
- 复杂筛选查询
- 多端兼容旧接口

---

## 九、改造完成后的目标状态

改造完成后，`record` 模块的日常记录能力应满足：

1. 同一类型在同一天可以创建多条记录
2. 每条记录都有独立 `id`
3. 任意一条记录都能被单独修改
4. 任意一条记录都能被单独删除
5. 月历打点逻辑保持正常
6. `/save` 不再存在

---

## 十、前端联调接口请求/返回示例

本模块统一返回结构为：

```json
{
  "code": 200,
  "message": "成功",
  "data": {},
  "total": null
}
```

其中：

- `code = 200` 表示成功
- `message` 成功时通常为 `成功`
- `data` 为实际业务数据
- `total` 在这些接口里通常为 `null`

### 10.1 获取月打点

**请求方式**

- `GET /athena/record/marks?year=2026&month=3`

**用途**

- 给日历页打点
- 即使某天有多条记录，这里也只会返回一次日期

**成功返回示例**

```json
{
  "code": 200,
  "message": "成功",
  "data": [
    "2026-03-11",
    "2026-03-12",
    "2026-03-20"
  ],
  "total": null
}
```

**前端处理建议**

- `data` 直接作为“本月有记录的日期列表”使用
- 不需要在这个接口里区分同一天有几条记录

---

### 10.2 获取某天全部记录详情

**请求方式**

- `GET /athena/record/detail?date=2026-03-11`

**用途**

- 打开某一天详情页
- 渲染当天全部记录列表
- 为每条记录保留 `id`，供后续编辑/删除使用

**成功返回示例**

```json
{
  "code": 200,
  "message": "成功",
  "data": [
    {
      "id": 123,
      "userId": 1001,
      "recordDate": "2026-03-11",
      "modeType": 2,
      "recordItemId": 101,
      "recordValue": "晚上一次"
    },
    {
      "id": 122,
      "userId": 1001,
      "recordDate": "2026-03-11",
      "modeType": 2,
      "recordItemId": 101,
      "recordValue": "上午一次"
    },
    {
      "id": 121,
      "userId": 1001,
      "recordDate": "2026-03-11",
      "modeType": 3,
      "recordItemId": 205,
      "recordValue": "轻微腹痛"
    }
  ],
  "total": null
}
```

**前端处理建议**

- `data` 是一个数组，不再假设“同一类型一天只有一条”
- 每一条记录都必须缓存它的 `id`
- 列表当前默认按 `id` 倒序返回，通常也就是“新建的在前面”

---

### 10.3 新增单条记录

**请求方式**

- `POST /athena/record`

**请求体示例**

```json
{
  "recordDate": "2026-03-11",
  "modeType": 2,
  "recordItemId": 101,
  "recordValue": "上午一次"
}
```

**字段说明**

- `recordDate`：记录日期，格式 `yyyy-MM-dd`
- `modeType`：模式类型
- `recordItemId`：记录项类型 id
- `recordValue`：记录内容

**成功返回示例**

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "id": 124,
    "userId": 1001,
    "recordDate": "2026-03-11",
    "modeType": 2,
    "recordItemId": 101,
    "recordValue": "上午一次"
  },
  "total": null
}
```

**前端处理建议**

- 每点击一次新增，都是新增一条，不再覆盖同类型旧记录
- 成功后建议使用返回的 `data` 直接插入当前列表
- 也可以重新请求一次 `/detail` 做列表刷新

**失败场景示例**

当缺少必要字段时，服务端会抛异常，例如：

- `记录日期不能为空`
- `模式类型不能为空`
- `记录类型不能为空`
- `记录内容不能为空`

前端可统一按错误消息弹 toast。

---

### 10.4 修改单条记录

**请求方式**

- `PUT /athena/record/{id}`

示例：

- `PUT /athena/record/124`

**请求体示例**

```json
{
  "modeType": 2,
  "recordValue": "晚上一次"
}
```

**说明**

第一版只允许修改：

- `modeType`
- `recordValue`

暂不支持修改：

- `recordDate`
- `recordItemId`

**成功返回示例**

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "id": 124,
    "userId": 1001,
    "recordDate": "2026-03-11",
    "modeType": 2,
    "recordItemId": 101,
    "recordValue": "晚上一次"
  },
  "total": null
}
```

**前端处理建议**

- 编辑时必须带具体 `id`
- 成功后可用返回的 `data` 替换本地列表中的对应项
- 不要再通过 `recordItemId + recordDate` 去猜要修改哪一条

**失败场景示例**

- 记录不存在：`记录不存在`
- 修改他人记录：`无权修改该记录`
- 请求体为空或内容为空：`记录内容不能为空`
- `modeType` 为空：`模式类型不能为空`

---

### 10.5 删除单条记录

**请求方式**

- `DELETE /athena/record/{id}`

示例：

- `DELETE /athena/record/124`

**请求体**

- 无

**成功返回示例**

```json
{
  "code": 200,
  "message": "成功",
  "data": "删除成功",
  "total": null
}
```

**前端处理建议**

- 删除时必须传具体记录 `id`
- 成功后直接从本地列表移除该项，或重新拉取 `/detail`
- 不要再通过把 `recordValue` 传空来表达删除

**失败场景示例**

- 记录不存在：`记录不存在`
- 删除他人记录：`无权删除该记录`

---

### 10.6 前端联调迁移清单

前端联调时请按下面方式迁移：

1. 停止调用旧接口：`POST /athena/record/save`
2. 新增记录改为：`POST /athena/record`
3. 编辑记录改为：`PUT /athena/record/{id}`
4. 删除记录改为：`DELETE /athena/record/{id}`
5. 详情页列表必须保留每条记录的 `id`
6. UI 层不要再默认“同一类型一天只能有一条”
7. 删除按钮必须显式走删除接口，不再通过空值提交实现删除

---

### 10.7 一组完整联调示例

#### 步骤 1：先查某天详情

请求：

- `GET /athena/record/detail?date=2026-03-11`

返回里拿到：

- 第一条 `id = 123`
- 第二条 `id = 122`

#### 步骤 2：新增一条同类型记录

请求体：

```json
{
  "recordDate": "2026-03-11",
  "modeType": 2,
  "recordItemId": 101,
  "recordValue": "第三次记录"
}
```

调用：

- `POST /athena/record`

结果：

- 后端返回新记录 `id`
- 同一天 `recordItemId = 101` 现在可以有 3 条

#### 步骤 3：修改其中一条

请求体：

```json
{
  "modeType": 2,
  "recordValue": "把上午一次改成中午一次"
}
```

调用：

- `PUT /athena/record/122`

结果：

- 只修改 `id = 122` 这一条
- 不影响 `id = 123` 或新插入那条

#### 步骤 4：删除其中一条

调用：

- `DELETE /athena/record/123`

结果：

- 只删除 `id = 123` 这一条
- 其他同天同类型记录保留
