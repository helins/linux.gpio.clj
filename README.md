# linux-gpio.clj

Handle [GPIO](https://github.com/dvlopt/linux-gpio.java) lines in a fast and
portable way from Clojure.

Based on [linux-gpio.java](https://github.com/dvlopt/linux-gpio.java). Go there
for rationale and background. In short, this API controls GPIO device from user
space by using the "new" Linux API which is convenient, standard, and offers a
some advantages over other methods (eg. automatic clean-up of resources when
lines are released).

## Usage

Read the
[API](https://dvlopt.github.io/doc/clojure/linux.gpio/dvlopt.linux.gpio.html).

Have a look at the [examples](./examples).

## License

Copyright © 2018 Adam Helinski

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
