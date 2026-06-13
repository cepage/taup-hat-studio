package org.tanzu.thstudio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tauphat")
public record TaupHatProperties(SecurityProperties security, GcsProperties gcs, FirebaseProperties firebase) {

    public record SecurityProperties(String allowedEmails, boolean localMode) {
        public SecurityProperties {
            if (allowedEmails == null) allowedEmails = "";
            // localMode defaults to false
        }
    }

    public record GcsProperties(String bucketName, String projectId) {
        public GcsProperties {
            if (bucketName == null) bucketName = "tauphat-assets";
            if (projectId == null) projectId = "";
        }
    }

    public record FirebaseProperties(String siteId, String apiKey, String authDomain, String projectId,
                                      String storageBucket, String messagingSenderId, String appId,
                                      String recaptchaSiteKey) {
        public FirebaseProperties {
            if (siteId == null) siteId = "";
            if (apiKey == null) apiKey = "";
            if (authDomain == null) authDomain = "";
            if (projectId == null) projectId = "";
            if (storageBucket == null) storageBucket = "";
            if (messagingSenderId == null) messagingSenderId = "";
            if (appId == null) appId = "";
            if (recaptchaSiteKey == null) recaptchaSiteKey = "";
        }
    }
}
