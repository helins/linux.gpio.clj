;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at https://mozilla.org/MPL/2.0/.


(ns helins.linux.gpio.example

  "Various GPIO examples."

  {:author "Adam Helinski"}

  (:require [helins.linux.gpio :as gpio]))


;;;;;;;;;;


(defn alternating-leds

  "Alternates leds.

   In the REPL, press CTRL-C in order to stop.


   Ex. (alternate {:device       0
                   :interval-ms  250
                   :line-numbers [17 27 22]})"

  ([line-numbers]

   (alternating-leds line-numbers
                     nil))


  ([line-numbers param+]

   (let [interval-ms (or (:interval-ms param+)
                         500)]
     (with-open [device (gpio/device (or (:device param+)
                                         0))
                 handle (gpio/handle device
                                     (reduce (fn add-led [line-number->line-options line-number]
                                               (assoc line-number->line-options
                                                      line-number
                                                      {:gpio/state false}))
                                             {}
                                             line-numbers)
                                     {:gpio/direction :output})]
       (let [buffer (gpio/buffer handle)]
         (loop [line-numbers' (cycle line-numbers)]
           (gpio/write handle
                       (-> buffer
                           gpio/clear-line+
                           (gpio/set-line (first line-numbers')
                                          true)))
           (Thread/sleep interval-ms)
           (recur (rest line-numbers'))))))))




(defn push-buttons

  "Given line numbers refering to push-buttons, logs every time a button is released until the timeout
   elapses.

   In the REPL, press CTRL-C in order to stop."

  ([line-numbers]

   (push-buttons line-numbers
                 nil))


  ([line-numbers param+]

   (let [timeout-ms (or (:timeout-ms param+)
                        10000)]
     (with-open [device  (gpio/device  (or (:device param+)
                                           0))
                 watcher (gpio/watcher device
                                       (reduce (fn add-button [line-number->watch-options line-number]
                                                 (assoc line-number->watch-options
                                                        line-number
                                                        {:gpio/edge-detection :falling
                                                         :gpio/direction      :input}))
                                               {}
                                               line-numbers))]
       (while true
         (println (if-some [event (gpio/event watcher
                                              timeout-ms)]
                    (format "%d  Button for line %d has been pressed"
                            (:gpio/nano-timestamp event)
                            (:gpio/tag event))
                    "Timeout !")))))))
