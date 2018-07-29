package com.github.lburgazzoli.camel.loaders;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("camel.route-loaders")
public class LoadersConfigurationProperties {
    /**
     * routes dir
     */
    public String path = "classpath:camel/*";
}
