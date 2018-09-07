
// ****************
//
// Setup
//
// ****************

l = components.get('log')
l.exchangeFormatter = function(e) {
    return "log - body=" + e.in.body + ", headers=" + e.in.headers
}

a = components.make('lll', 'org.apache.camel.component.log.LogComponent')
a.exchangeFormatter = function(e) {
    return "aaa - body=" + e.in.body + ", headers=" + e.in.headers
}


// ****************
//
// Functions
//
// ****************

function proc(e) {
    e.getIn().setHeader('RandomValue', Math.floor((Math.random() * 100) + 1))
}

// ****************
//
// Route
//
// ****************

from('timer:js?period=1s')
    .routeId('nashorn')
    .setBody()
        .constant('nashorn')
    .process(proc)
    .to('log:nashorn')
    .to('lll:nashorn')