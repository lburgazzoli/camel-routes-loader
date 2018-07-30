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
import java.io.Reader;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.util.DelegatingScript;
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
            (source, builder) -> {
                //https://stackoverflow.com/questions/31236550/defining-a-default-global-java-object-to-nashorn-script-engine
                final ScriptEngineManager manager = new ScriptEngineManager();
                final ScriptEngine engine = manager.getEngineByName("nashorn");

                // get JS "global" object
                Object global = engine.eval("this");
                // get JS "Object" constructor object
                Object jsObject = engine.eval("Object");

                Invocable invocable = (Invocable) engine;

                // bind properties of this builder to JS global object
                invocable.invokeMethod(jsObject, "bindProperties", global, builder);

                try (InputStream is = source.getInputStream()) {
                    engine.eval(new InputStreamReader(is));
                }
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
            (source, builder) -> {
                try(Context context = Context.create()) {
                    // add this builder instance to javascript language
                    // bindings
                    context.getBindings("js").putMember("builder", builder);

                    // move builder's methods to global scope so builder's
                    // dsl can be invoke directly
                    context.eval(
                        "js",
                        "m = Object.keys(builder)\n" +
                        "m.forEach(element => global[element] = builder[element])"
                    );

                    // remove bindings
                    context.getBindings("js").removeMember("builder");

                    try (InputStream is = source.getInputStream()) {
                        context.eval(
                            Source.newBuilder("js", new InputStreamReader(is), "").build()
                        );
                    }
                }
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
            (source, builder) -> {
                CompilerConfiguration cc = new CompilerConfiguration();
                cc.setScriptBaseClass(DelegatingScript.class.getName());

                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                GroovyShell sh = new GroovyShell(cl, new Binding(), cc);

                try (InputStream is = source.getInputStream()) {
                    Reader reader = new InputStreamReader(is);
                    DelegatingScript script = (DelegatingScript) sh.parse(reader);

                    // set the delegate target
                    script.setDelegate(builder);
                    script.run();
                }
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
