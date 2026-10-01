package org.tanzu.thstudio.commission;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CommissionProperties.class)
public class CommissionConfig {
}
