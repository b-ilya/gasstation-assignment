# GasStation model

This is model of gas station that clients can use to buy fuel.

It has simple api with a couple of methods, but tries to keep best performance.  
It supports concurrent clients and several of them can pump simultaneously
based on the number of pumps. If number of clients is exceeding number of pumps 
they will wait in selected pump's line.

### Task description

[assignment.md](assignment.md)
