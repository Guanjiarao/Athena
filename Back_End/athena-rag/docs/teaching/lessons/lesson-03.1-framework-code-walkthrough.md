# 第 3.1 课：`framework` 代码补读与关键实现拆解

## 一、课程定位

本节是第 3 课 `framework` 基础设施层的补充课。

如果说第 3 课解决的是“这些基础设施为什么要存在”，那么第 3.1 课解决的是：

> 这些抽象在代码里到底是怎么落地的？

本节会围绕几个你最容易卡住的点，直接结合源码片段做拆解：

- `Result<T>` / `Results`
- `GlobalExceptionHandler`
- `UserContext`
- `MessageQueueProducer` / `RocketMQProducerAdapter`
- `DataBaseConfiguration`
- `RagTraceContext`
- `SseEmitterSender`

## 二、统一响应体：`Result<T>` 为什么这样设计？

先看 `Result<T>` 的核心结构：

```java
public class Result<T> implements Serializable {

    public static final String SUCCESS_CODE = "0";

    private String code;
    private String message;
    private T data;
    private String requestId;

    public boolean isSuccess() {
        return SUCCESS_CODE.equals(code);
    }
}
```

### 你要关注的不是字段，而是 3 个设计意图

#### 1. 成功失败协议统一

这里不是 controller 想返回什么就返回什么，而是所有接口默认都收敛到：

- `code`
- `message`
- `data`
- `requestId`

这意味着前端不需要为不同接口维护不同解析逻辑。

#### 2. `SUCCESS_CODE = "0"`

这不是随便写的。它说明：

- 项目把“状态码”视为业务协议的一部分
- 后续错误码体系大概率统一使用字符串
- 成功和失败都通过统一字段表达，而不是依赖 HTTP 200/500 来直接承载业务语义

#### 3. `requestId` 是为排查预留的

虽然这个类本身没填充 `requestId` 的逻辑，但字段已经预留好了。这说明作者一开始就考虑了链路排查，而不是只顾接口能不能返回数据。

## 三、`Results`：为什么不让大家到处 `new Result<>()`

源码非常直接：

```java
public final class Results {

    public static Result<Void> success() {
        return new Result<Void>()
                .setCode(Result.SUCCESS_CODE);
    }

    public static <T> Result<T> success(T data) {
        return new Result<T>()
                .setCode(Result.SUCCESS_CODE)
                .setData(data);
    }

    public static Result<Void> failure() {
        return new Result<Void>()
                .setCode(BaseErrorCode.SERVICE_ERROR.code())
                .setMessage(BaseErrorCode.SERVICE_ERROR.message());
    }
}
```

### 这里真正的价值是什么？

#### 1. 返回协议不靠“团队自觉”维持

如果没有 `Results`，每个人都可以自己这样写：

```java
return new Result<>().setCode("0").setData(data);
```

问题是：

- 有人会忘写 `message`
- 有人会成功时顺手写别的 code
- 有人失败时写自定义结构

有了 `Results`，统一协议就从“口头规范”变成了“统一入口”。

#### 2. 业务层表达更聚焦

业务层只需要表达：

- 我成功了
- 我失败了
- 我带什么数据

而不需要每次重复拼协议细节。

## 四、`GlobalExceptionHandler`：到底是怎么把异常收口的？

先看最常见的一段：参数校验异常。

```java
@ExceptionHandler(value = MethodArgumentNotValidException.class)
public Result<Void> validExceptionHandler(HttpServletRequest request, MethodArgumentNotValidException ex) {
    BindingResult bindingResult = ex.getBindingResult();
    FieldError firstFieldError = CollectionUtil.getFirst(bindingResult.getFieldErrors());
    String exceptionStr = Optional.ofNullable(firstFieldError)
            .map(FieldError::getDefaultMessage)
            .orElse(StrUtil.EMPTY);
    log.error("[{}] {} [ex] {}", request.getMethod(), getUrl(request), exceptionStr);
    return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), exceptionStr);
}
```

### 这一段要看懂 4 件事

#### 1. 参数校验失败被归类为客户端错误

返回的是：

- `BaseErrorCode.CLIENT_ERROR`

这就说明系统认为：

> 参数不合法，不是服务端故障，而是请求方的问题。

#### 2. 只取第一个字段错误

这是一个典型工程取舍：

- 不追求一次性返回全部错误
- 而是优先给用户最直接、最可读的一条错误信息

这对前端交互通常更友好。

#### 3. 返回协议还是统一走 `Results.failure(...)`

也就是说，异常虽然类型不同，但对外响应格式不变。

#### 4. 日志里保留了请求方法和 URL

说明作者在设计异常处理器时，不只是想着“返回前端什么”，还想着“线上如何排查”。

---

再看自定义业务异常处理：

```java
@ExceptionHandler(value = {AbstractException.class})
public Result<Void> abstractException(HttpServletRequest request, AbstractException ex) {
    if (ex.getCause() != null) {
        log.error("[{}] {} [ex] {}", request.getMethod(), request.getRequestURL().toString(), ex, ex.getCause());
        return Results.failure(ex);
    }
    StringBuilder stackTraceBuilder = new StringBuilder();
    stackTraceBuilder.append(ex.getClass().getName()).append(": ").append(ex.getErrorMessage()).append("\n");
    StackTraceElement[] stackTrace = ex.getStackTrace();
    for (int i = 0; i < Math.min(5, stackTrace.length); i++) {
        stackTraceBuilder.append("\tat ").append(stackTrace[i]).append("\n");
    }
    log.error("[{}] {} [ex] {} \n\n{}", request.getMethod(), request.getRequestURL().toString(), ex, stackTraceBuilder);
    return Results.failure(ex);
}
```

### 这段为什么值得学？

#### 1. 业务异常和未知异常分开处理

说明项目并不是“所有错误一锅端”。

#### 2. 对 `AbstractException` 进行了受控日志输出

如果异常有 `cause`，按正常方式打。
如果没有 `cause`，就手工拼一个简版堆栈，只打印前 5 层。

这背后是在平衡两件事：

- 问题要能排查
- 日志不能毫无节制地爆炸

#### 3. 最终返回依然统一

外部看到的是统一协议，内部保留的是丰富语义。

这就是基础设施层最理想的状态。

## 五、`UserContext`：为什么 `requireUser()` 这个方法很关键？

源码如下：

```java
public final class UserContext {

    private static final TransmittableThreadLocal<LoginUser> CONTEXT = new TransmittableThreadLocal<>();

    public static void set(LoginUser user) {
        CONTEXT.set(user);
    }

    public static LoginUser get() {
        return CONTEXT.get();
    }

    public static LoginUser requireUser() {
        LoginUser user = CONTEXT.get();
        if (user == null) {
            throw new ClientException("未获取到当前登录用户");
        }
        return user;
    }
}
```

### 这里最容易忽略，但最值钱的是 `requireUser()`

很多项目只会提供：

- `get()`

然后所有业务自己判空。

这样的问题是：

- 业务层重复写很多空判断
- 有的地方忘判空
- 有的地方判空后抛的异常语义不一致

而 `requireUser()` 的价值就在于：

> “必须有用户上下文” 这个约束，被封装成了基础设施级 API。

这样业务层可以明确分两种调用语义：

### 场景 A：允许匿名

```java
String userId = UserContext.getUserId();
```

### 场景 B：必须登录

```java
LoginUser loginUser = UserContext.requireUser();
```

这会让代码语义非常清晰。

---

再看它为什么用 TTL：

```java
private static final TransmittableThreadLocal<LoginUser> CONTEXT = new TransmittableThreadLocal<>();
```

这意味着作者已经明确考虑：

- 线程池
- 异步任务
- 并行处理
- 流式链路

如果你以后自己做 RAG 项目，这一行其实是非常重要的经验点。

## 六、消息发送抽象：先看接口，再看实现

先看接口：

```java
public interface MessageQueueProducer {

    SendResult send(String topic, String keys, String bizDesc, Object body);

    void sendInTransaction(String topic, String keys, String bizDesc, Object body,
                           Consumer<Object> localTransaction);
}
```

### 为什么这个接口设计得很像“业务友好 API”？

因为它没有把 RocketMQ 原生复杂度暴露出去。

业务真正关心的是：

- 发到哪个 topic
- 业务 key 是什么
- 这条消息是什么业务
- 内容是什么
- 是否需要事务语义

而不是：

- Message 怎么构造
- Header 怎么塞
- 事务监听器怎么注册
- RocketMQTemplate 用哪个方法发

这就是抽象层该做的事。

---

再看普通消息发送实现：

```java
@Override
public SendResult send(String topic, String keys, String bizDesc, Object body) {
    keys = StrUtil.isEmpty(keys) ? UUID.randomUUID().toString() : keys;

    Message<MessageWrapper<Object>> message = MessageBuilder
            .withPayload(MessageWrapper.builder().keys(keys).body(body).build())
            .setHeader(MessageConst.PROPERTY_KEYS, keys)
            .build();

    SendResult sendResult;
    try {
        sendResult = rocketMQTemplate.syncSend(topic, message);
    } catch (Throwable ex) {
        log.error("[生产者] {} - 消息发送失败，topic: {}, keys: {}", bizDesc, topic, keys, ex);
        throw ex;
    }

    log.info("[生产者] {} - 发送结果: {}, 消息ID: {}, Keys: {}", bizDesc, sendResult.getSendStatus(), sendResult.getMsgId(), keys);
    return sendResult;
}
```

### 这段代码你要重点看懂什么？

#### 1. `keys` 自动兜底生成

如果业务没传 key，也不会裸发，而是自动生成 UUID。

这说明作者默认希望：

- 每条消息尽量都有可追踪 key
- 即使业务没提供，系统也补一个最基本的标识

#### 2. 统一消息体包装

它不是直接发 body，而是包装成：

- `MessageWrapper.builder().keys(keys).body(body).build()`

这说明消息协议也在被统一，不希望不同业务各自定义消息载荷外层结构。

#### 3. `bizDesc` 只服务于日志，但非常实用

这个字段不会进业务载荷，但对日志定位非常有帮助。

很多项目消息日志难看，就是因为缺少这种“业务描述字段”。

---

再看事务消息：

```java
@Override
public void sendInTransaction(String topic, String keys, String bizDesc, Object body,
                              Consumer<Object> localTransaction) {
    keys = StrUtil.isEmpty(keys) ? UUID.randomUUID().toString() : keys;
    String txId = UUID.randomUUID().toString();

    transactionListener.registerLocalTransaction(txId, localTransaction);

    Message<MessageWrapper<Object>> message = MessageBuilder
            .withPayload(MessageWrapper.builder().keys(keys).body(body).build())
            .setHeader(MessageConst.PROPERTY_KEYS, keys)
            .setHeader(DelegatingTransactionListener.HEADER_TX_ID, txId)
            .setHeader(DelegatingTransactionListener.HEADER_TOPIC, topic)
            .build();

    TransactionSendResult sendResult;
    try {
        sendResult = rocketMQTemplate.sendMessageInTransaction(topic, message, null);
    } catch (Throwable ex) {
        log.error("[生产者] {} - 事务消息发送失败，topic: {}, keys: {}", bizDesc, topic, keys, ex);
        throw ex;
    }
}
```

### 这里的关键理解点

#### 1. 本地事务逻辑被作为 `Consumer<Object>` 传入

这意味着业务层可以表达：

- “这是一条事务消息”
- “这个消息成功进入 half 状态后，我要执行这段本地事务”

这是很清晰的业务编程模型。

#### 2. `txId` 被显式挂到消息头里

这说明作者在设计消息事务链路时，已经在考虑：

- 回查
- 关联本地事务
- 事务状态跟踪

#### 3. RocketMQ 原生细节没有污染业务层

业务层调用的是 `sendInTransaction(...)`，而不是自己拼各种 header。

这就是封装成功的标志。

## 七、数据库配置：为什么 `DataBaseConfiguration` 很短，但很关键

虽然这类类很短，但它决定了数据库层的“默认运行方式”。

你在第 3 课里已经知道，它至少负责：

- MyBatis-Plus 分页插件
- 自动填充处理器

这一类配置最大的价值是：

> 把“所有业务都会重复依赖的数据库约定”统一在底层固定下来。

比如如果分页方言不统一、自动填充不统一，上层业务就会不断写重复逻辑。

这类类的代码不长，但影响面极大。

## 八、`RagTraceContext`：为什么它比普通 `traceId` 更进一步？

第 3 课里你已经知道它维护：

- `traceId`
- `taskId`
- `nodeStack`

这里你需要更进一步理解的是：

### 普通后端 trace 常见只做到：

- 一个请求一个 traceId

### 但 RAG 链路往往还需要：

- 子任务 taskId
- 当前执行到哪个节点
- 多节点嵌套关系

也就是说，它不是只想表达“这是同一次请求”，还想表达：

> “这次请求当前走到了哪一个执行节点，它的父子层级是什么。”

这对后面分析：

- 查询改写耗时
- 检索耗时
- rerank 耗时
- prompt 组装耗时
- 模型调用耗时

都非常关键。

## 九、`SseEmitterSender`：为什么专门封一个发送器？

很多人第一次写 SSE，会直接在业务里反复写：

```java
emitter.send(...);
emitter.complete();
emitter.completeWithError(...);
```

这种写法短期能跑，长期很乱，因为：

- 关闭状态不好统一控制
- 重复关闭容易出问题
- 流式发送失败后的处理逻辑容易分散

所以 `SseEmitterSender` 的工程意义是：

- 统一发送 API
- 统一关闭逻辑
- 统一异常结束语义
- 统一幂等关闭行为

这一类类的价值，不在“写法难”，而在“能把容易写乱的细节统一起来”。

## 十、这一节你最该补上的认知

如果第 3 课你理解的是“framework 的概念地图”，那第 3.1 课你应该补上的，就是下面这些更贴近代码的认知：

1. `Result<T>` 不是 DTO，而是接口协议对象。
2. `Results` 不是 util，而是统一的响应构造入口。
3. `GlobalExceptionHandler` 的重点不是 catch，而是“异常语义到接口协议”的转换。
4. `requireUser()` 这种方法，本质上是在把业务约束收口为基础设施 API。
5. `MessageQueueProducer` 的价值不在发 MQ，而在屏蔽 RocketMQ 的协议细节。
6. `RagTraceContext` 和 `SseEmitterSender` 体现了 `ragent/framework` 已经明显在为 RAG 场景服务，而不是停留在普通 CRUD 框架层面。

## 十一、建议你现在怎么读源码

读完这一节后，我建议你按这个顺序自己再回看源码：

### 第一步：先看“接口协议”

- `Result`
- `Results`
- `GlobalExceptionHandler`

重点看：系统怎么统一对外说话。

### 第二步：再看“上下文约束”

- `UserContext`
- `RagTraceContext`

重点看：线程切换和链路透传怎么解决。

### 第三步：再看“中间件封装”

- `MessageQueueProducer`
- `RocketMQProducerAdapter`
- `SseEmitterSender`

重点看：业务层如何避免直接依赖底层中间件细节。

## 十二、课后问题

你可以带着下面几个问题继续想：

1. 如果把 `Results` 去掉，只保留 `Result<T>`，项目会不会还能跑？会，但为什么工程质量会下降？
2. `requireUser()` 和 `getUserId()` 的并存，为什么比只保留一个 `get()` 更好？
3. `MessageQueueProducer` 如果直接暴露 `RocketMQTemplate`，业务层会开始承担哪些本不该承担的复杂度？
4. 在 RAG 系统里，为什么 `nodeStack` 比单纯一个 `traceId` 更有价值？
