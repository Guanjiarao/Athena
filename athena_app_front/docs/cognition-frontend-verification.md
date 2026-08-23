# 认知闭环 V1 前端验收清单

## 自动检查

在项目根目录执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline
.\gradlew.bat :app:assembleDebug --offline
```

单元测试覆盖稳定错误码、网关 Token 特殊错误、三个草稿决定分支、四个反馈分支、首页九态和 non-null JSON 缺省字段解析。

## 真实账号主链路

1. 登录一个独立测试账号，确认“我的身体认知”显示“真实账号数据”。
2. 在文章详情分别创建 RELATED、QUESTION、KNOWLEDGE_ONLY 三类线索。
3. 对刚保存的线索执行一次撤销；对已经进入草稿的线索确认撤销会提示不可撤销。
4. 进入“我的身体线索与疑问”，检查三个标签及分页。
5. 在待整理标签点击“帮我整理”，打开 READY 草稿并核对四段文案及来源证据。
6. 分别使用三个独立草稿验证接受为主题、只保存知识、拒绝。
7. 对接受后生成的行动分别验证反馈；SKIPPED 的结果不应产生 evidenceId。
8. 返回健康首页，确认页面直接反映服务端 summaryState、activeTopic、nextAction 和 latestInsight。

## TC-23：重新登录和跨设备

1. 在设备 A 完成“创建线索 → 整理 → 接受主题 → 行动反馈”，记录主题标题和证据数，不记录或导出 Token。
2. 退出账号后重新登录，确认线索、草稿决定、主题和反馈仍存在。
3. 在设备 B 或一台全新模拟器登录同一账号。
4. 确认首页 summaryState、主题、证据数、行动状态与设备 A 一致。
5. 在设备 B 新增一条线索，再回到设备 A 刷新确认可见。

验收记录应包含测试日期、App commit、设备/系统版本、账号 userId、各步骤结果和失败截图；不要在记录中保存手机号、验证码或 Token。
