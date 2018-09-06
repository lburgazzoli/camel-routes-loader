/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.lburgazzoli.camel.route.autoconfigure;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.function.Function;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleBindings;

import com.google.gson.Gson;
import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spring.boot.CamelContextConfiguration;
import org.apache.camel.util.function.ThrowingBiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Configuration
@ConditionalOnProperty(prefix = "camel.routes.loader", name = "enabled")
@EnableConfigurationProperties(RoutesLoaderConfigurationProperties.class)
public class RoutesLoaderAutoConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoutesLoaderAutoConfiguration.class);
    private static final ScriptEngineManager ENGINE_MANAGER = new ScriptEngineManager();

    // ********************************
    //
    // Loaders
    //
    // ********************************

    @Bean
    @Conditional(ScriptingJs.class)
    public CamelContextConfiguration loadGraalJSRoutes(
        final ApplicationContext applicationContext,
        final RoutesLoaderConfigurationProperties configuration) {

        return new RoutesLoader(
            applicationContext,
            configuration,
            ".js",
            new ScriptingLoader("js")
        );
    }

    @Bean
    @Conditional(ScriptingGroovy.class)
    public CamelContextConfiguration loadGroovyRoutes(
            final ApplicationContext applicationContext,
            final RoutesLoaderConfigurationProperties configuration) {

        return new RoutesLoader(
            applicationContext,
            configuration,
            ".groovy",
            new ScriptingLoader("groovy")
        );
    }

    // ********************************
    //
    // Helpers
    //
    // ********************************

    private static final class ScriptingGroovy implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return ENGINE_MANAGER.getEngineByName("groovy") != null;
        }
    }

    private static final class ScriptingJs implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return ENGINE_MANAGER.getEngineByName("js") != null;
        }
    }

    private static final class ScriptingLoader implements ThrowingBiConsumer<Resource, RouteBuilder, Exception> {
        private final String language;

        public ScriptingLoader(String language) {
            this.language = language;
        }

        // TODO: bind utility methods such as those from BuilderSupport
        @Override
        public void accept(Resource resource, RouteBuilder builder) throws Exception {
            final ScriptEngineManager manager = new ScriptEngineManager();
            final ScriptEngine engine = manager.getEngineByName(this.language);
            final Bindings bindings = new SimpleBindings();

            bindings.put("components", new CamelComponents(builder.getContext()));
            bindings.put("properties", new CamelProperties(builder.getContext()));
            bindings.put("from", (Function<String, RouteDefinition>) uri -> builder.from(uri));

            try (InputStream is = resource.getInputStream()) {
                engine.eval(new InputStreamReader(is), bindings);
            }
        }
    }

    public static class CamelComponents {
        private CamelContext context;

        public CamelComponents(CamelContext context) {
            this.context = context;
        }

        public Component get(String scheme) {
            return context.getComponent(scheme, true);
        }

        public Component alias(String scheme, String alias) {
            final String json = context.getRuntimeCamelCatalog().componentJSonSchema(scheme);
            final Map<String, Object> component = new Gson().fromJson(json, Map.class);
            final String type = (String)((Map<String, Object>)component.get("component")).get("javaType");
            final Class<?> clazz = context.getClassResolver().resolveClass(type);
            final Component inst = (Component)context.getInjector().newInstance(clazz);

            context.addComponent(alias, inst);

            return inst;
        }
    }

    public static class CamelProperties {
        private CamelContext context;

        public CamelProperties(CamelContext context) {
            this.context = context;
        }

        public String resolve(String property) {
            try {
                return context.resolvePropertyPlaceholders(property);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
