
// ****************
//
// Setup
//
// ****************

a = components.make('ggg', 'org.apache.camel.component.log.LogComponent')
a.exchangeFormatter = { return "ggg - body=" + it.in.body + ", headers=" + it.in.headers }

// ****************
//
// Route
//
// ****************

from('timer:groovy?period=1s')
    .routeId('groovy')
    .process { it.in.body = UUID.randomUUID().toString() }
    .to('ggg:groovy')