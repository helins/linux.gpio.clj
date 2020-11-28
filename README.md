# dvlopt.linux.gpio

[![Clojars
Project](https://img.shields.io/clojars/v/dvlopt/linux.gpio.svg)](https://clojars.org/dvlopt/linux.gpio)

Handle [GPIO](https://github.com/dvlopt/linux-gpio.java) lines in a fast and
portable way from Clojure.

Based on [linux-gpio.java](https://github.com/dvlopt/linux-gpio.java). Go there
for rationale and background. In short, this API controls GPIO device from user
space by using the "new" Linux API which is convenient, standard, and offers a
some advantages over other methods (eg. automatic clean-up of resources when
lines are released).

Compatible with Linux 4.8 and higher, tested on the Raspberry Pi 3.

For information about running Clojure on the Raspberry Pi, here is a
[guide](https://github.com/dvlopt/clojure-raspberry-pi).

## Usage

Read the
[API](https://cljdoc.org/d/dvlopt/linux.gpio/1.0.0/api/dvlopt.linux.gpio). The
namespace description summerizes everything there is to know.

You might want to have a look at the [examples](./examples).

Attention, at least read permission is needed for the used GPIO devices, which
is enough even for writing to outputs.

For instance :

```clj
(require '[dvlopt.linux.gpio :as gpio])


;; Alternating between 2 leds every time a button is released.
;;
;; After opening a GPIO device, we need a handle for driving the leds and a watcher
;; for monitoring the button. A buffer is used in conjunction with the handle in
;; order to describe the state of the leds.


(with-open [device         (gpio/device "/dev/gpiochip0")
            led-handle     (gpio/handle device
                                        {17 {::gpio/state false
                                             ::gpio/tag   :led-1}
                                         18 {::gpio/state true
                                             ::gpio/tag   :led-2}}
                                        {::gpio/direction :output})
            button-watcher (gpio/watcher device
                                         {22 {::gpio/direction :input}})]
  (let [buffer (gpio/buffer led-handle)]
    (loop [leds (cycle [:led-1
                        :led-2])]
      (gpio/write led-handle
                  (gpio/set-lines buffer
                                  {(first  leds) true
                                   (second leds) false}))
      (gpio/event button-watcher)
      (recur (rest leds)))))
```

## License

Copyright © 2018 Adam Helinski

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
