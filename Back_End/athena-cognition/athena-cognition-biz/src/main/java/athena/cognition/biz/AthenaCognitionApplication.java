package athena.cognition.biz;

import athena.cognition.biz.rpc.RecordInternalFeignApi;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(clients = {RecordInternalFeignApi.class})
public class AthenaCognitionApplication {

    public static void main(String[] args) {
        SpringApplication.run(AthenaCognitionApplication.class, args);
    }
}
