package athena.insight.biz.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI insightOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Athena Insight API")
                        .description("Athena 洞察模块接口文档")
                        .version("v1.0.0")
                        .contact(new Contact().name("Athena Team"))
                        .license(new License().name("Apache 2.0")));
    }
}
