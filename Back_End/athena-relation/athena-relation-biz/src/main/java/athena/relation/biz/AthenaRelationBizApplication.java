package athena.relation.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("athena.relation.biz.mapper")
@EnableFeignClients("athena")
public class AthenaRelationBizApplication {

    public static void main(String[] args) {
        SpringApplication.run(AthenaRelationBizApplication.class, args);
    }

}
