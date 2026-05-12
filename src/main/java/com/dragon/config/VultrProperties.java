package com.dragon.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "vultr.inference")
@Getter
@Setter
public class VultrProperties {

    @Name("api-key")
    private String apiKey;
}