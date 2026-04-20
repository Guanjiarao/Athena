package athena.athenaframework.result;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


public class Result<T> {
    private Integer code;       // 状态码，如 200, 400
    private String message;     // 提示信息
    private T data;             // 返回数据
    private Long total;         // 分页总数

    public Result() {
    }

    public Result(Integer code, String message, T data, Long total) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.total = total;
    }

    // 成功，无数据
    public static <T> Result<T> ok() {
        return new Result<>(200, "成功", null, null);
    }

    // 成功，有数据
    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "成功", data, null);
    }

    // 成功，有列表 + 总数
    public static <T> Result<List<T>> ok(List<T> data, Long total) {
        return new Result<>(200, "成功", data, total);
    }

    // 失败
    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null, null);
    }

    /**
     * 获取
     * @return code
     */
    public Integer getCode() {
        return code;
    }

    /**
     * 设置
     * @param code
     */
    public void setCode(Integer code) {
        this.code = code;
    }

    /**
     * 获取
     * @return message
     */
    public String getMessage() {
        return message;
    }

    /**
     * 设置
     * @param message
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 获取
     * @return data
     */
    public T getData() {
        return data;
    }

    /**
     * 设置
     * @param data
     */
    public void setData(T data) {
        this.data = data;
    }

    /**
     * 获取
     * @return total
     */
    public Long getTotal() {
        return total;
    }

    /**
     * 设置
     * @param total
     */
    public void setTotal(Long total) {
        this.total = total;
    }

    public String toString() {
        return "Result{code = " + code + ", message = " + message + ", data = " + data + ", total = " + total + "}";
    }
}