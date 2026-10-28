package com.memme.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.fcm")
public class FcmProperties {

    private String projectId = "";
    private String serviceAccountJson = "";

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getServiceAccountJson() {
        return serviceAccountJson;
    }

    public void setServiceAccountJson(String serviceAccountJson) {
        this.serviceAccountJson = serviceAccountJson;
    }

    @Override
    public String toString() {
        return "FcmProperties[projectId=" + projectId
                + ", serviceAccountJson=<redacted>]";
    }
}
