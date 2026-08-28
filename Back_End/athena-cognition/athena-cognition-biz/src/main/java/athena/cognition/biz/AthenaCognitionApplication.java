package athena.cognition.biz;

import athena.cognition.biz.rpc.RecordInternalFeignApi;
import athena.cognition.biz.rpc.agent.CognitionAgentFeignApi;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableFeignClients(clients = {RecordInternalFeignApi.class, CognitionAgentFeignApi.class})
public class AthenaCognitionApplication {

    public static void main(String[] args) {
        SpringApplication.run(AthenaCognitionApplication.class, args);
    }
}
