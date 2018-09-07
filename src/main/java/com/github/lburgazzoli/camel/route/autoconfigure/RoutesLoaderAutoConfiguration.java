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

import javax.script.ScriptEngineManager;

import com.github.lburgazzoli.camel.route.scripting.GraalJsRouteLoader;
import com.github.lburgazzoli.camel.route.scripting.GroovyRouteLoader;
import com.github.lburgazzoli.camel.route.scripting.NashornRouteLoader;
import org.apache.camel.spring.boot.CamelContextConfiguration;
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
    @Conditional(NashornScriptCondition.class)
    public CamelContextConfiguration nashornRoutes(
        final ApplicationContext applicationContext,
        final RoutesLoaderConfigurationProperties configuration) {

        return new RoutesLoader(
            applicationContext,
            configuration,
            ".js",
            new NashornRouteLoader()
        );
    }


    @Bean
    @Conditional(GraalJsScriptCondition.class)
    public CamelContextConfiguration graalJsRoutes(
        final ApplicationContext applicationContext,
        final RoutesLoaderConfigurationProperties configuration) {

        return new RoutesLoader(
            applicationContext,
            configuration,
            ".gjs",
            new GraalJsRouteLoader()
        );
    }

    @Bean
    @Conditional(GroovyCondition.class)
    public CamelContextConfiguration groovyRoutes(
            final ApplicationContext applicationContext,
            final RoutesLoaderConfigurationProperties configuration) {

        return new RoutesLoader(
            applicationContext,
            configuration,
            ".groovy",
            new GroovyRouteLoader()
        );
    }

    // ********************************
    //
    // Helpers
    //
    // ********************************

    private static final class GroovyCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return ENGINE_MANAGER.getEngineByName("groovy") != null;
        }
    }

    private static final class NashornScriptCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return ENGINE_MANAGER.getEngineByName("nashorn") != null && ENGINE_MANAGER.getEngineByName("graal.js") == null;
        }
    }

    private static final class GraalJsScriptCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return ENGINE_MANAGER.getEngineByName("graal.js") != null;
        }
    }
}
