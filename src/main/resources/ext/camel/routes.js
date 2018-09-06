
// ******************************************************
//
// Setup
//
// ******************************************************

l = components.get('log')
l.exchangeFormatter = function(e) {
    return "log - body=" + e.in.body + ", headers=" + e.in.headers
}


a = components.alias('log', 'aaa')
a.exchangeFormatter = function(e) {
    return "aaa - body=" + e.in.body + ", headers=" + e.in.headers
}

// ******************************************************
//
// Functions
//
// ******************************************************

function proc(e) {
    e.getIn().setHeader('RandomValue', Math.floor((Math.random() * 100) + 1))
}

// ******************************************************
//
// Route
//
// ******************************************************

from('timer:js?period=1s')
    .setBody()
        .constant('js')
    .process(proc)
    .to('log:js')
    .to('aaa:js')