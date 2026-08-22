package athena.ground.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("athena.ground.biz.domain.mapper")
@EnableFeignClients(basePackages = "athena")
@EnableScheduling
public class AthenaGroundApplication {
    public static void main(String[] args) {
        SpringApplication.run(AthenaGroundApplication.class, args);
    }
}
