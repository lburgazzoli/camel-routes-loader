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
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.util.DelegatingScript;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.boot.CamelContextConfiguration;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
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

    @Bean
    @Conditional(JSNashorn.class)
    public CamelContextConfiguration loadJSRoutes(
            final ApplicationContext applicationContext,
            final RoutesLoaderConfigurationProperties configuration) {

        return new RoutesLoader(
            applicationContext,
            configuration,
            ".js",
            (Resource source) -> {
                return new RouteBuilder() {
                    public void configure() throws Exception {
                        //https://stackoverflow.com/questions/31236550/defining-a-default-global-java-object-to-nashorn-script-engine
                        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");

                        // get JavaScript "global" object
                        Object global = engine.eval("this");
                        // get JS "Object" constructor object
                        Object jsObject = engine.eval("Object");

                        Invocable invocable = (Invocable) engine;

                        // "bind" properties of this to JS global object
                        invocable.invokeMethod(jsObject, "bindProperties", global, this);

                        try (InputStream is = source.getInputStream()) {
                            engine.eval(new InputStreamReader(is));
                        }
                    }
                };
            }
        );
    }

    @Bean
    @Conditional(JSGraal.class)
    @ConditionalOnClass(Context.class)
    public CamelContextConfiguration loadGraalJSRoutes(
        final ApplicationContext applicationContext,
        final RoutesLoaderConfigurationProperties configuration) {

        return new RoutesLoader(
            applicationContext,
            configuration,
            ".gjs",
            (Resource source) -> {
                return new RouteBuilder() {
                    public void configure() throws Exception {
                        try(Context context = Context.create()) {

                            // add this builder instance to javascript language
                            // bindings
                            context.getBindings("js").putMember("builder", this);

                            // move builder's methods to global scope
                            context.eval(
                                "js",
                                "m = Object.keys(builder)\n" +
                                    "m.forEach((element) => {\n" +
                                    "    global[element] = builder[element]\n" +
                                    "});"
                            );

                            try (InputStream is = source.getInputStream()) {
                                context.eval(
                                    Source.newBuilder("js", new InputStreamReader(is), "").build()
                                );
                            }
                        }
                    }
                };
            }
        );
    }

    @Bean
    @ConditionalOnClass(GroovyShell.class)
    public CamelContextConfiguration loadGroovyRoutes(
            final ApplicationContext applicationContext,
            final RoutesLoaderConfigurationProperties configuration) {

        return new RoutesLoader(
            applicationContext,
            configuration,
            ".groovy",
            (Resource source) -> {
                return new RouteBuilder() {
                    public void configure() throws Exception {
                        CompilerConfiguration cc = new CompilerConfiguration();
                        cc.setScriptBaseClass(DelegatingScript.class.getName());

                        GroovyShell sh = new GroovyShell(Thread.currentThread().getContextClassLoader(), new Binding(), cc);

                        try (InputStream is = source.getInputStream()) {
                            DelegatingScript script = (DelegatingScript) sh.parse(new InputStreamReader(is));
                            script.setDelegate(this);
                            script.run();
                        }
                    }
                };
            }
        );
    }

    private static final class JSNashorn implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return new ScriptEngineManager().getEngineByName("nashorn") != null;
        }
    }

    private static final class JSGraal implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String vmName = System.getProperty("java.vm.name");
            if (vmName != null) {
                return vmName.toLowerCase().startsWith("graalvm");
            }

            return false;
        }
    }
}
