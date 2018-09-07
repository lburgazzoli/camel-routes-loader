package com.github.lburgazzoli.camel.route.scripting;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;

public class Components {
    private CamelContext context;

    public Components(CamelContext context) {
        this.context = context;
    }

    public Component get(String scheme) {
        return context.getComponent(scheme, true);
    }

    public Component put(String scheme, Component instance) {
        context.addComponent(scheme, instance);

        return instance;
    }

    public Component make(String scheme, String type) {
        final Class<?> clazz = context.getClassResolver().resolveClass(type);
        final Component instance = (Component)context.getInjector().newInstance(clazz);

        context.addComponent(scheme, instance);

        return instance;
    }
}
