;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at https://mozilla.org/MPL/2.0/.


(ns user

  "For daydreaming at the REPL."

  (:require [helins.linux.gpio         :as gpio]
            [helins.linux.gpio.example :as gpio.example]
            [helins.void               :as void])
  (:import com.sun.jna.Memory
           (io.helins.linux.gpio GpioBuffer
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
           (io.helins.linux.gpio.internal NativeGpioChipInfo
                                          NativeGpioHandleRequest
                                          NativeGpioEventData
                                          NativeGpioEventRequest
                                          NativeGpioLineInfo)))


;;;;;;;;;;


(comment



  )
