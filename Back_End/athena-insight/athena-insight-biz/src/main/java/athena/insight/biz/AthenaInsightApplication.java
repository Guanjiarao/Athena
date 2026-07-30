package athena.insight.biz;

import athena.insight.biz.rpc.GroundFeignApi;
import athena.insight.biz.rpc.InsightGroundFeignApi;
import athena.insight.biz.rpc.InsightRecordFeignApi;
import athena.insight.biz.rpc.RecordFeignApi;
import athena.insight.biz.rpc.UserAuthFeignApi;
import athena.userauth.api.UserFeignApi;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("athena.insight.biz.domain.mapper")
@EnableFeignClients(clients = {InsightGroundFeignApi.class, InsightRecordFeignApi.class, UserFeignApi.class})
public class AthenaInsightApplication {

    public static void main(String[] args) {
        SpringApplication.run(AthenaInsightApplication.class, args);
    }
}
