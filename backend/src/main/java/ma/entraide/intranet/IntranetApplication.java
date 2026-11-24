package ma.entraide.intranet;

import ma.entraide.intranet.common.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class IntranetApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntranetApplication.class, args);
    }
}
