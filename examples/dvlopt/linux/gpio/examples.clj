(ns dvlopt.linux.gpio.examples

  "Various GPIO examples."

  {:author "Adam Helinski"}

  (:require [dvlopt.linux.gpio :as gpio])
  (:import java.lang.AutoCloseable))




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


  ([line-numbers {:as   options
                  :keys [device
                         interval-ms]
                  :or   {device      0
                         interval-ms 500}}]

   (with-open [^AutoCloseable device' (gpio/device device)
               ^AutoCloseable handle  (gpio/handle device'
                                                   (reduce (fn add-led [line-number->line-options line-number]
                                                             (assoc line-number->line-options
                                                                    line-number
                                                                    {::gpio/state false}))
                                                           {}
                                                           line-numbers)
                                                   {::gpio/direction :output})]
     (let [buffer (gpio/buffer handle)]
       (loop [line-numbers' (cycle line-numbers)]
         (gpio/write handle
                     (-> buffer
                         gpio/clear-lines
                         (gpio/set-line (first line-numbers')
                                        true)))
         (Thread/sleep interval-ms)
         (recur (rest line-numbers')))))))




(defn push-buttons

  "Given line numbers refering to push-buttons, logs everytime a button is released until the timeout
   elapses.

   In the REPL, press CTRL-C in order to stop."

  ([line-numbers]

   (push-buttons line-numbers
                 nil))


  ([line-numbers {:as   options
                  :keys [device
                         timeout-ms]
                  :or   {device     0
                         timeout-ms 10000}}]

   (with-open [^AutoCloseable device' (gpio/device  device)
               ^AutoCloseable watcher (gpio/watcher device'
                                                    (reduce (fn add-button [line-number->watch-options line-number]
                                                              (assoc line-number->watch-options
                                                                     line-number
                                                                     {::gpio/edge-detection :falling
                                                                      ::gpio/direction      :input}))
                                                            {}
                                                            line-numbers))]
     (while true
       (println (if-some [event (gpio/event watcher
                                            timeout-ms)]
                  (format "%d  Button for line %d has been pressed"
                          (::gpio/nano-timestamp event)
                          (::gpio/tag event))
                  "Timeout !"))))))
