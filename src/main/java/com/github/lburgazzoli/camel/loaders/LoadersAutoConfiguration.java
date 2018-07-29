package com.github.lburgazzoli.camel.loaders;

import java.io.InputStream;
import java.io.InputStreamReader;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.util.DelegatingScript;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.boot.CamelContextConfiguration;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
@EnableConfigurationProperties(LoadersConfigurationProperties.class)
public class LoadersAutoConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadersAutoConfiguration.class);

    @Bean
    public CamelContextConfiguration loadRoutesConfiguration(
            final ApplicationContext applicationContext,
            final LoadersConfigurationProperties configuration) {

        return new CamelContextConfiguration() {
            public void beforeApplicationStart(CamelContext camelContext) {
                try {
                    Resource[] sources = applicationContext.getResources(configuration.path);
                    for (Resource source : sources) {
                        RouteBuilder builder = null;

                        if (source.isFile() && source.getFilename().endsWith(".js")) {
                            LOGGER.info("Loading additional Camel routes from: {}", source);

                            builder = new RouteBuilder() {
                                public void configure() throws Exception {
                                    ScriptEngine engine = new ScriptEngineManager().getEngineByName("javascript");
                                    Object global = engine.eval("this");
                                    Object jsObject = engine.eval("Object");
                                    Invocable invocable = (Invocable)engine;

                                    // "bind" properties of this to JS global object
                                    invocable.invokeMethod(jsObject, "bindProperties", global, this);

                                    try (InputStream is = source.getInputStream()) {
                                        engine.eval(new InputStreamReader(is));
                                    }
                                }
                            };
                        }

                        if (source.isFile() && source.getFilename().endsWith(".groovy")) {
                            LOGGER.info("Loading additional Camel routes from: {}", source);

                            builder = new RouteBuilder() {
                                public void configure() throws Exception {
                                    CompilerConfiguration cc = new CompilerConfiguration();
                                    cc.setScriptBaseClass(DelegatingScript.class.getName());

                                    GroovyShell sh = new GroovyShell(camelContext.getApplicationContextClassLoader(), new Binding(), cc);

                                    try (InputStream is = source.getInputStream()) {
                                        DelegatingScript script = (DelegatingScript)sh.parse(new InputStreamReader(is));
                                        script.setDelegate(this);
                                        script.run();
                                    }

                                }
                            };
                        }

                        if (builder != null) {
                            camelContext.addRoutes(builder);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("", e);
                }
            }

            public void afterApplicationStart(CamelContext camelContext) {
            }
        };
    }
}
