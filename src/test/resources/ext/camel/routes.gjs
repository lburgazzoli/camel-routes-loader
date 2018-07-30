
from('timer:gjs?period=1s')
    .setBody()
        .constant('graaljs')
    .to('log:gjs?showAll=false&multiline=false')