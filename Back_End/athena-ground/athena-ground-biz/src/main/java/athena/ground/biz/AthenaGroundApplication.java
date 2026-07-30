package athena.ground.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("athena.ground.biz.domain.mapper")
@EnableFeignClients(basePackages = "athena")
public class AthenaGroundApplication {
    public static void main(String[] args) {
        SpringApplication.run(AthenaGroundApplication.class, args);
    }
}
