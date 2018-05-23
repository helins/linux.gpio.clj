(ns dvlopt.linux.gpio

  " ? "

  {:author "Adam Helinski"}

  (:refer-clojure :exclude [read])
  (:require [dvlopt.void :as void])
  (:import io.dvlopt.linux.LinuxException
           io.dvlopt.linux.errno.Errno
           (io.dvlopt.linux.gpio GpioBuffer
                                 GpioChipInfo
                                 GpioDevice
                                 GpioEdgeDetection
                                 GpioEvent
                                 GpioEventHandle
                                 GpioEventRequest
                                 GpioEventWatcher
                                 GpioHandle
                                 GpioHandleRequest
                                 GpioFlags
                                 GpioLine
                                 GpioLineInfo)))




;;;;;;;;;; Declarations


(declare -flags)




;;;;;;;;;; Default values for options


(def defaults

  ""

  {::edge-detection :rising-and-falling})




;;;;;;;;;; Private data


(def ^:private -errors--handle

  ""

  {Errno/EBUSY  :already-requested
   Errno/EINVAL :invalid-argument
   Errno/ENOTTY :inappropriate-device})




(def ^:private -errors--info

  ""

  {Errno/ENOTTY :inappropriate-device})




;;;;;;;;;; Private functions


(defn -error

  ""

  ([linux-exception]

   (-error linux-exception
           nil))


  ([^LinuxException linux-exception errno->tag]

   (let [errno-value (.getErrno linux-exception)]
     {::error {::errno  errno-value
               ::reason (reduce-kv (fn find-error [errno->tag ^Errno errno tag]
                                     (if (= (.-value errno)
                                            errno-value)
                                       (reduced tag)
                                       errno->tag))
                                   :unknown
                                   errno->tag)}})))




(defn- -event

  ""

  [^GpioEvent event]

  {::edge           (if (.isRising event)
                      :rising
                      :falling)
   ::tag            (.getId event)
   ::nano-timestamp (.getNanoTimestamp event)})




(defn -event-handle

  ""

  [device line-number options]

  (let [edge-detection-kw         (void/obtain ::edge-detection
                                               options
                                               defaults)
        edge-detection            (condp identical?
                                         edge-detection-kw
                                    :rising             GpioEdgeDetection/RISING
                                    :falling            GpioEdgeDetection/FALLING
                                    :rising-and-falling GpioEdgeDetection/RISING_AND_FALLING)
        [^GpioEventHandle handle
                          error]  (try
                                    [(.requestEvent ^GpioDevice (::device device)
                                                    (GpioEventRequest. line-number
                                                                       edge-detection
                                                                       (-flags options)))
                                     nil]
                                    (catch LinuxException e
                                      [nil
                                       (-error e
                                               -errors--handle)]))]
    (let [result {::edge-detection edge-detection-kw}]
      (if error
        (merge result
               error)
        (assoc result
               ::event-handle
               handle)))))




(defn- -flags

  ""
  
  ^GpioFlags

  [opts]

  (let [^GpioFlags flags (GpioFlags.)]
    (when (::active-low? opts)
      (.setActiveLow flags
                     true))
    (when-some [direction (::direction opts)]
      (condp identical?
             direction
        :input  (.setInput  flags)
        :output (.setOutput flags)))
    (when (::open-drain? opts)
      (.setOpenDrain flags
                     true))
    (when (::open-source? opts)
      (.setOpenSource flags
                      true))
    flags))




;;;;;;;;;; Opening and releasing GPIO resources


(defn device

  ""

  [device-path]

  (let [path    (if (number? device-path)
                  (str "/dev/gpiochip"
                       device-path)
                  device-path)
        result  {::path path
                 ::type :device}
        [device
         error] (try
                  [(GpioDevice. ^String path)
                   nil]
                  (catch LinuxException e
                    [nil
                     (-error e
                             {Errno/EACCES :permission-denied
                              Errno/ENOENT :no-such-device})]))]
    (merge result
           (or error
               {::device device}))))




(defn handle

  ""

  ([device lines]

   (handle device
           lines
           nil))


  ([device line-number->line-opts handle-opts]

   (let [^GpioHandleRequest req       (GpioHandleRequest.)
                            tag->line (reduce-kv (fn add-tag [tag->line line-number line-opts]
                                                   (assoc tag->line
                                                          (get line-opts
                                                               ::tag
                                                               line-number)
                                                          (if (::state line-opts)
                                                            (.addLine req
                                                                      line-number
                                                                      true)
                                                            (.addLine req
                                                                      line-number))))
                                                 {}
                                                 line-number->line-opts)]
     (some->> (::consumer handle-opts)
              (.setConsumer req))
     (.setFlags req
                (-flags handle-opts))
     (let [[handle
            error] (try
                     [(.requestHandle ^GpioDevice (::device device)
                                      req)
                      nil]
                     (catch LinuxException e
                       [nil
                        (-error e
                                -errors--handle)]))
           result  {::type :handle}]
       (merge result
              (or error
                  {::handle    handle
                   ::tag->line tag->line}))))))




(defn watcher

  [device line-number->options]

  (let [result                     {::type :watcher}
        [^GpioEventWatcher watcher
                           error]  (try
                                     [(GpioEventWatcher.)
                                      nil]
                                     (catch LinuxException e
                                       [nil
                                        (-error e
                                                {Errno/EMFILE :process-file-descriptors-used
                                                 Errno/ENFILE :system-file-descritors-used})]))]
    (if watcher
      (merge result
             {::watcher watcher}
             (reduce-kv (fn prepare-event-handle [hmap line-number options]
                          (let [event-handle (-event-handle device
                                                            line-number
                                                            options)
                                error        (or (when (contains? event-handle
                                                                  ::error)
                                                   event-handle)
                                                 (try
                                                   (.addHandle watcher
                                                               ^GpioEventHandle (::event-handle event-handle)
                                                               line-number)
                                                   nil
                                                   (catch LinuxException e
                                                     (-error e
                                                             {Errno/ENOSPC :max-watches}))))]
                            (if error
                              (update hmap
                                      ::line-number->error
                                      assoc
                                      line-number
                                      error)
                              (let [tag (get options
                                             ::tag
                                             line-number)]
                                (-> hmap
                                    (update ::line-number->tag
                                            assoc
                                            line-number
                                            tag)
                                    (update ::tag->event-handle
                                            assoc
                                            tag
                                            event-handle)
                                    (update ::tag->line
                                            assoc
                                            tag
                                            (.getLine ^GpioEventHandle (::event-handle event-handle))))))))
                        {::line-number->error {}
                         ::line-number->tag   {}
                         ::tag->event-handle  {}
                         ::tag->line          {}}
                        line-number->options))
      (assoc result
             :error
             error))))




(defn close

  ""

  [resource]

  (try
    (condp identical?
           (::type resource)
      :device  (some-> ^GpioDevice (::device resource)
                       .close)
      :handle  (some-> ^GpioHandle (::handle resource)
                       .close)
      :watcher (when-some [^GpioEventWatcher watcher (::watcher resource)]
                 (.close watcher)
                 (doseq [[_ event-handle] (::tag->event-handle resource)]
                   (.close ^GpioEventHandle (::event-handle event-handle))))
      nil)
    nil
    (catch LinuxException e
      (-error e))))




;;;;;;;;;; Requesting information


(defn describe-chip

  ""

  [^GpioDevice device]

  (try
    (let [^GpioChipInfo info (.requestChipInfo ^GpioDevice (::device device))]
      {::label   (.getLabel info)
       ::n-lines (.getLines info)
       ::name    (.getName  info)})
    (catch LinuxException e
      (-error e
              -errors--info))))




(defn describe-line 

  ""

  [^GpioDevice device line-number]

  (try
    (let [^GpioLineInfo info  (.requestLineInfo ^GpioDevice (::device device)
                                                line-number)
          ^GpioFlags    flags (.getFlags info)]
      (void/assoc-some {::active-low?      (.isActiveLow flags)
                        ::direction        (if (.isInput flags)
                                             :input
                                             :output)
                        ::line             (.getLine info)
                        ::open-drain?      (.isOpenDrain flags)
                        ::open-source?     (.isOpenSource flags)
                        ::used?            (.isUsed info)}
                       ::consumer (.getConsumer info)
                       ::name     (.getName info)))
    (catch LinuxException e
      (-error e
              -errors--info))))




(defn describe-lines

  ""

  [^GpioDevice device]

  (let [chip (describe-chip device)]
    (if (contains? chip
                   ::error)
      chip
      (map (fn single-line [line-number]
             (describe-line device
                            line-number))
           (range (::n-lines chip))))))




;;;;;;;;;;


(defn buffer

  ""


  [resource]

  {::buffer    (GpioBuffer.)
   ::tag->line (::tag->line resource)})




(defn clear-lines

  ""

  [buffer]

  (.clear ^GpioBuffer (::buffer buffer))
  buffer)




(defn get-line

  ""

  [buffer tag]

  (.get ^GpioBuffer (::buffer buffer)
        (get (::tag->line buffer)
             tag)))




(defn get-lines

  ""

  ([buffer]

   (get-lines buffer
              (keys (::tag->line buffer))))


  ([buffer tags]

   (let [^GpioBuffer buffer'   (::buffer buffer)
                     tag->line (::tag->line buffer)]
     (reduce (fn line-by-tag [tag->state tag]
               (assoc tag->state
                      tag
                      (.get buffer'
                            (get tag->line
                                 tag))))
             {}
             tags))))




(defn set-line

  ""

  [buffer tag state]

  (.set ^GpioBuffer (::buffer buffer)
        (get (::tag->line buffer)
             tag)
        state)
  buffer)




(defn set-lines

  ""

  [buffer tag->state]

  (let [^GpioBuffer buffer'   (::buffer buffer)
                    tag->line (::tag->line buffer)]
    (doseq [[tag state] tag->state]
      (.set buffer'
            (get tag->line
                 tag)
            state)))
  buffer)




(defn toggle-line

  ""

  [buffer tag]

  (.toggle ^GpioBuffer (::buffer buffer)
           (get (::tag->line buffer)
                tag))
  buffer)




(defn toggle-lines

  ""

  ([buffer]

   (toggle-lines buffer
                 (keys (::tag->line buffer))))


  ([buffer tags]

   (let [^GpioBuffer buffer'   (::buffer buffer)
                     tag->line (::tag->line buffer)]
     (doseq [tag tags]
       (.toggle buffer'
                (get tag->line
                     tag))))
   buffer))




;;;;;;;;;; IO - Handle


(defn read

  ""

  [handle buffer]

  (try
    (.read ^GpioHandle (::handle handle)
           ^GpioBuffer (::buffer buffer))
    nil
    (catch LinuxException e
      (-error e
              {Errno/EBADF :handle-closed}))))





(defn write

  ""

  [handle buffer]

  (try
    (.write ^GpioHandle (::handle handle)
            ^GpioBuffer (::buffer buffer))
    nil
    (catch LinuxException e
      (-error e
              {Errno/EBADF :handle-closed}))))




;;;;;;;;;; IO - Watcher


(defn poll

  ""

  [watcher buffer tag]

  (try
    (.read ^GpioEventHandle (::event-handle (get (::tag->event-handle watcher)
                                                 tag))
           ^GpioBuffer      (::buffer buffer))
    {::state (get-line buffer
                       tag)}
    (catch LinuxException e
      (-error e
              {Errno/EBADF ::watcher-closed}))))




(defn wait

  ""

  ([watcher]

   (wait watcher
         -1))


  ([watcher timeout-ms]

   (let [event ^GpioEvent (GpioEvent.)]
     (try
       (when (.waitForEvent ^GpioEventWatcher (::watcher watcher)
                            event
                            timeout-ms)
         (update (-event event)
                 ::tag
                 (::line-number->tag watcher)))
       (catch LinuxException e
         (-error e
                 {Errno/EBADF :watcher-closed}))))))
