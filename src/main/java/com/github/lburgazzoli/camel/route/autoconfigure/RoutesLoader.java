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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.boot.CamelContextConfiguration;
import org.apache.camel.util.function.ThrowingBiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;

final class RoutesLoader implements CamelContextConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoutesLoader.class);

    private final ApplicationContext applicationContext;
    private final RoutesLoaderConfigurationProperties configuration;
    private final String extension;
    private final ThrowingBiConsumer<Resource, RouteBuilder, Exception> mapper;

    RoutesLoader(
            ApplicationContext applicationContext,
            RoutesLoaderConfigurationProperties configuration,
            String extension,
            ThrowingBiConsumer<Resource, RouteBuilder, Exception> mapper) {

        this.applicationContext = applicationContext;
        this.configuration = configuration;
        this.extension = extension;
        this.mapper = mapper;
    }

    @Override
    public void beforeApplicationStart(CamelContext camelContext) {
        try {
            for (String location: configuration.getLocations()) {
                for (Resource source : applicationContext.getResources(location)) {
                    if (source.getFilename().endsWith(extension)) {
                        LOGGER.info("Loading additional Camel routes from: {}", source);

                        camelContext.addRoutes(new RouteBuilder() {
                            @Override
                            public void configure() throws Exception {
                                mapper.accept(source, this);
                            }
                        });
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("", e);
        }
    }

    @Override
    public void afterApplicationStart(CamelContext camelContext) {
    }
}
