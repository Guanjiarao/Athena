package athena.record.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
@MapperScan("athena.record.biz.domain.mapper")
public class AthenaRecordBizApplication {

    public static void main(String[] args) {
        SpringApplication.run(AthenaRecordBizApplication.class, args);
    }

}
