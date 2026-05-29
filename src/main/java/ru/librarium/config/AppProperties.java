package ru.librarium.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "librarium")
public class AppProperties {

    private List<String> adminEmails = new ArrayList<>();
    private Auth auth = new Auth();
    private Firebase firebase = new Firebase();

    @Getter
    @Setter
    public static class Auth {
        private int rememberDays = 30;
        private String rememberSecret = "change-me";
    }

    @Getter
    @Setter
    public static class Firebase {
        private String projectId;
    }
}