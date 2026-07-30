package athena.comment.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("athena.comment.biz.domain.mapper")
@EnableFeignClients(basePackages = "athena")
public class AthenaCommentBizApplication {
    public static void main(String[] args) {
        SpringApplication.run(AthenaCommentBizApplication.class, args);
    }
}
