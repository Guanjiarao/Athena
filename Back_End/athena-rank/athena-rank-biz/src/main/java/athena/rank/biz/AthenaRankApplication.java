package athena.rank.biz;

import org.springframework.boot.SpringApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@MapperScan("athena.rank.biz.mapper")
@SpringBootApplication
@EnableFeignClients(basePackages = "athena")
public class AthenaRankApplication {

    public static void main(String[] args) {
        SpringApplication.run(AthenaRankApplication.class, args);
    }
}
