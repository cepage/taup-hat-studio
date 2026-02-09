package org.tanzu.thstudio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tauphat")
public record TaupHatProperties(SecurityProperties security, GcsProperties gcs, FirebaseProperties firebase) {

    public record SecurityProperties(String allowedEmail, boolean localMode) {
        public SecurityProperties {
            if (allowedEmail == null) allowedEmail = "";
            // localMode defaults to false
        }
    }

    public record GcsProperties(String bucketName, String projectId) {
        public GcsProperties {
            if (bucketName == null) bucketName = "tauphat-assets";
            if (projectId == null) projectId = "";
        }
    }

    public record FirebaseProperties(String siteId) {
        public FirebaseProperties {
            if (siteId == null) siteId = "";
        }
    }
}
