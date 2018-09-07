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
package com.github.lburgazzoli.camel.route.scripting;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.util.DelegatingScript;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.util.function.ThrowingBiConsumer;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.springframework.core.io.Resource;

public class GroovyRouteLoader implements ThrowingBiConsumer<Resource, RouteBuilder, Exception> {
    @Override
    public void accept(Resource resource, RouteBuilder builder) throws Exception {
        CompilerConfiguration cc = new CompilerConfiguration();
        cc.setScriptBaseClass(DelegatingScript.class.getName());

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        GroovyShell sh = new GroovyShell(cl, new Binding(), cc);

        try (InputStream is = resource.getInputStream()) {
            Reader reader = new InputStreamReader(is);
            DelegatingScript script = (DelegatingScript) sh.parse(reader);

            // set the delegate target
            script.setDelegate(new Delegate(builder));
            script.run();
        }
    }

    private static class Delegate {
        private final RouteBuilder builder;

        public final CamelContext context;
        public final Components components;

        public Delegate(RouteBuilder builder) {
            this.builder = builder;
            this.context = builder.getContext();
            this.components = new Components(builder.getContext());
        }

        public RouteDefinition from(String endpoint) {
            return builder.from(endpoint);
        }
    }

}
