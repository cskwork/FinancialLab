package io.github.cskwork.finlab.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.StreamWriteFeature;

@Configuration(proxyBeanMethods = false)
public class JsonConfiguration {
    @Bean JsonMapperBuilderCustomizer plainDecimals() {
        return builder -> builder.enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN);
    }
}
