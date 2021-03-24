# Linux.GPIO

[![Clojars
Project](https://img.shields.io/clojars/v/io.helins/linux.gpio.svg)](https://clojars.org/io.helins/linux.gpio)
 
[![cljdoc badge](https://cljdoc.org/badge/io.helins/linux.gpio)](https://cljdoc.org/d/io.helins/linux.gpio)

Handle [GPIO](https://github.com/helins/linux-gpio.java) lines in a fast and
portable way from Clojure.

Based on [linux-gpio.java](https://github.com/helins/linux-gpio.java). Go there
for rationale and background.

In short, this API controls GPIO devices from user space by using the "new"
Linux API which is convenient, standard, and offers some advantages over
other methods (eg. automatic clean-up of resources when lines are released).

A GPIO device is a driver located in "/dev". For instance, on the Raspbian
operating system running on a Raspberry Pi, one can control all GPIO lines by using
the "/dev/gpiochip0" GPIO device.

Compatible with Linux 4.8 and higher, tested on the Raspberry Pi 3.


## Usage

This is a very brief overview.

The [full API is available on Cljdoc](https://cljdoc.org/d/io.helins/linux.gpio/1.0.0/api/helins.linux.gpio).

Small examples are available in the [helins.linux.gpio.example](../main/src/example/helins/linux/gpio/example.clj).

Attention, at least read permission is needed for the used GPIO devices, which
is enough even for writing to outputs.

For instance :

```clj
(require '[helins.linux.gpio :as gpio])


;; Alternating between 2 leds every time a button is released.
;;
;; After opening a GPIO device, we need a handle for driving the leds and a watcher
;; for monitoring the button. A buffer is used in conjunction with the handle in
;; order to describe the state of the leds.


(with-open [device         (gpio/device "/dev/gpiochip0")
            led-handle     (gpio/handle device
                                        {17 {:gpio/state false
                                             :gpio/tag   :led-1}
                                         18 {:gpio/state true
                                             :gpio/tag   :led-2}}
                                        {::gpio/direction :output})
            button-watcher (gpio/watcher device
                                         {22 {:gpio/direction :input}})]
  (let [buffer (gpio/buffer led-handle)]
    (loop [leds (cycle [:led-1
                        :led-2])]
      (gpio/write led-handle
                  (gpio/set-line+ buffer
                                  {(first  leds) true
                                   (second leds) false}))
      (gpio/event button-watcher)
      (recur (rest leds)))))
```

## License

Copyright © 2018 Adam Helinski

Licensed under the term of the Mozilla Public License 2.0, see LICENSE.
