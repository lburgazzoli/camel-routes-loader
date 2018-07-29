
from('timer:groovy?period=1s')
    .to('log:groovy?showAll=false&multiline=false')