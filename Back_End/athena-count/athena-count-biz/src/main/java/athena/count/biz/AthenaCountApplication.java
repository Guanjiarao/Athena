package athena.count.biz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients(basePackages = "athena")
@EnableScheduling
public class AthenaCountApplication {

    public static void main(String[] args) {
        SpringApplication.run(AthenaCountApplication.class, args);
    }
}
