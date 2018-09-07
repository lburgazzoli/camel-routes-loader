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
import java.util.function.Function;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleBindings;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.util.function.ThrowingBiConsumer;
import org.springframework.core.io.Resource;

public class NashornRouteLoader implements ThrowingBiConsumer<Resource, RouteBuilder, Exception> {
    @Override
    public void accept(Resource resource, RouteBuilder builder) throws Exception {
        final ScriptEngineManager manager = new ScriptEngineManager();
        final ScriptEngine engine = manager.getEngineByName("nashorn");
        final Bindings bindings = new SimpleBindings();

        bindings.put("context", builder.getContext());
        bindings.put("components", new Components(builder.getContext()));
        bindings.put("from", (Function<String, RouteDefinition>) uri -> builder.from(uri));

        try (InputStream is = resource.getInputStream()) {
            engine.eval(new InputStreamReader(is), bindings);
        }
    }
}
