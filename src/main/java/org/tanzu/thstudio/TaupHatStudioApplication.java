package org.tanzu.thstudio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.tanzu.thstudio.config.TaupHatProperties;

@SpringBootApplication
@EnableConfigurationProperties(TaupHatProperties.class)
public class TaupHatStudioApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaupHatStudioApplication.class, args);
    }
}
