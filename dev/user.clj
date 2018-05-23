(ns user

  "For daydreaming in the repl."

  (:require [clojure.spec.alpha              :as s]
            [clojure.spec.gen.alpha          :as gen]
            [clojure.spec.test.alpha         :as st]
            [clojure.test.check.clojure-test :as tt]
            [clojure.test.check.generators   :as tgen]
            [clojure.test.check.properties   :as tprop]
            [clojure.test                    :as t]
            [criterium.core                  :as ct]
            [dvlopt.linux.gpio               :as gpio]
            [dvlopt.void                     :as void])
  (:import com.sun.jna.Memory
           (io.dvlopt.linux.gpio GpioBuffer
                                 GpioChipInfo
                                 GpioDevice
                                 GpioEdgeDetection
                                 GpioEvent
                                 GpioEventHandle
                                 GpioEventRequest
                                 GpioEventWatcher
                                 GpioFlags
                                 GpioHandle
                                 GpioHandleRequest
                                 GpioLine
                                 GpioLineInfo)
           (io.dvlopt.linux.gpio.internal NativeGpioChipInfo
                                          NativeGpioHandleRequest
                                          NativeGpioEventData
                                          NativeGpioEventRequest
                                          NativeGpioLineInfo)))




;;;;;;;;;;


(comment


  )
