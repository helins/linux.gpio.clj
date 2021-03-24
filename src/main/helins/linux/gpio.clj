(ns helins.linux.gpio

  "This namespace provides utilities for handling a GPIO device. Compatible with Linux 4.8 and higher.

   Access to a GPIO device can be obtained by using the `device` function. Three things can then be accomplished :

       - Request some information about the GPIO device or a specific GPIO line.
       - Request a handle for driving one or several lines at once.
       - Request a watcher for efficiently monitoring one or several lines.

   A handle controls all the lines it is responsible for at once. Whether those lines are actually driven exactly at
   the same time, atomically, depends on the underlying driver and is opaque to this library. Once a handle is created
   with the `handle` function on the appropriate GPIO device, one or several dedicated buffers can be created with the
   `buffer` function. The purpose of a buffer is to represent and/or manipulate the state of the lines controlled by a
   handle (up to 64). The state of a line can either be HIGH (true) or LOW (false).

   A watcher is used essentially for interrupts. Requested events, such as a line transitioning from LOW to HIGH, are
   queued by the kernel until read. However, Linux not being a real-time OS, such \"interrupts\" are imperfect and should
   not be used for critical tasks where a simple microcontroller will be of a better fit.
  
   Lines are abstracted by associating a line number with a tag (ie. any value meant to be more representative of what the
   line does, often a keyword).
  
   As usual, all IO functions might throw if anything goes wrong and do not forget to use `close` or the \"with-open idiom\"
   on all acquired IO resources. Closing a GPIO device do not close related handles and watchers.


   Here is a description of the keywords typically used throughout this library :

    ::active-low?
      Lines are typically active-high, meaning they become active when HIGH (true) and inactive when LOW (false). Active-low
      lines reverse this logical flow. They become active when LOW and inactive when HIGH.

    ::consumer
      An arbitrary string can be supplied when creating a handle. If a line is in use and associated with a consumer, it will
      show up when querying its state using `describe-line`. It allows for tracking who is using what.

    ::direction
      A line can either be used as :input or :output, never both at the same time. All lines associated
      with a handle must be of the same direction.

    ::label
      The operating system might associate a GPIO device and individual lines with string labels.

    ::line-number
      A single GPIO device can represent at most 64 lines. Hence, a line number is between 0 and 63 inclusive.

    ::name
      The operating system might associate a GPIO device and individual lines with string names.

    ::open-drain?
      Lines can act as open drains (ie. can only be driven to LOW).

    ::open-source?
      Lines can be open sources (ie. can only be driven to HIGH).

    ::state
      The state of a line can either be logical HIGH (true) or logical LOW (false).

    ::tag
      Instead of using raw line numbers which don't really mean anything, they can be associated with any value such
      as a string or a keyword. Besides being more descriptive, the program remains valid when the user dedices to use
      different lines while creating a handle (as long as tags remain the same)."

  {:author "Adam Helinski"}

  (:require [helins.void :as void])
  (:import (io.helins.linux.gpio GpioBuffer
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
                                 GpioLineInfo)
           java.lang.AutoCloseable)
  (:refer-clojure :exclude [read]))




;;;;;;;;;; Default values for options


(def defaults

  "Default values for argument options."

  {::edge-detection :rising-and-falling})




;;;;;;;;;;


(defn close

  "Closes a GPIO resource such as a device or a handle.
  
   It may be easier to use the standard `with-open` macro."

  [^AutoCloseable resource]

  (.close resource)
  nil)




;;;;;;;;;;


(defn device

  "Opens a GPIO device either by specifying a full path or by giving the number of the device.
  
   Those devices are located at '/dev/gpiochip$X' where $X is a number.

   Read permission must be granted, which is enough even for writing to outputs.

   Attention, closing a GPIO device does not close related handles and watchers."

  ^AutoCloseable

  [device-path]

  (if (number? device-path)
    (GpioDevice. ^int device-path)
    (GpioDevice. ^String device-path)))




(defn describe-chip

  "Requests a description of the given GPIO device.
  
   Returns a map containing :

     ::label (optional)
       Cf. Namespace description

     ::n-lines
       Number of available lines for that chip.

     ::name (optional)
       Cf. Namespace description."

  [^GpioDevice device]

  (let [info (.requestChipInfo device)]
    (void/assoc {::n-lines (.getLines info)}
                ::label (.getLabel info)
                ::name  (.getName  info))))




(defn describe-line 

  "Requests a description of the given line.
  
   Returns a map which may contain :

     ::active-low?
     ::consumer (optional)
     ::direction
       Cf. Namespace description

     ::line-number
       Number of the line.

     ::name (optional)
     ::open-drain?
     ::open-source?
       Cf. Namespace description

     ::used?
       Is this line currently in use ?"

  [^GpioDevice device line-number]

  (let [info  (.requestLineInfo device
                                line-number)
        flags (.getFlags info)]
    (void/assoc {::active-low?  (.isActiveLow flags)
                 ::direction    (if (.isInput flags)
                                  :input
                                  :output)
                 ::line-number  (.getLine info)
                 ::open-drain?  (.isOpenDrain flags)
                 ::open-source? (.isOpenSource flags)
                 ::used?        (.isUsed info)}
                ::consumer (.getConsumer info)
                ::name     (.getName info))))




;;;;;;;;;;


(defn- -flags

  ;; Produces a GpioFlags given options.
  
  ^GpioFlags

  [options]

  (let [^GpioFlags flags (GpioFlags.)]
    (when (::active-low? options)
      (.setActiveLow flags
                     true))
    (when-some [direction (::direction options)]
      (condp identical?
             direction
        :input  (.setInput  flags)
        :output (.setOutput flags)))
    (when (::open-drain? options)
      (.setOpenDrain flags
                     true))
    (when (::open-source? options)
      (.setOpenSource flags
                      true))
    flags))




;;;;;;;;;;


(defprotocol ^:private IPure

  ;; Retrieves the raw Java type from the original library.

  (^:private -raw-type [this]))




;;;;;;;;;;


(defn- -get-gpio-line

  ;; Retrieves a GpioLine given a tag or throw if not found.

  [tag->GpioLine tag]

  (when-not (contains? tag->GpioLine
                       tag)

    (throw (IllegalArgumentException. (format "GPIO buffer is not handling tag = %s"
                                              tag))))
  (get tag->GpioLine
       tag))




(defprotocol IBuffereable

  "For objects that can work with a GPIO buffer."

  (buffer ^AutoCloseable [this]

    "Produces a GPIO buffer representing the state of up to 64 lines.

     It implements `IBuffer` and works with tags associated with `this`.

     It does not do any IO on its own as it is meant to be used in conjunction with a
     handle or a watcher."))




(defprotocol IBuffer

  "Reading or writing the state of lines in a buffer before or after doing some IO.
  
   Ex. (write some-handle
              (-> some-buffer
                  (clear-lines)
                  (set-lines {:green-led true
                              :red-led   false})))"

  (clear-lines [buffer]

    "Sets all lines of the given buffer to LOW (false).")


  (get-line [buffer tag]

    "Retrieves the state of a single line from the given buffer.")


  (get-lines [buffer]
             [buffer tags]

    "Retrieves the state of several lines (or all of them if nothing is specified) from the given buffer.
    
     Returns a map of tag -> state.")


  (set-line [buffer tag state]

    "Sets the state of a single line in the given buffer.")


  (set-lines [buffer tag->state]

    "Sets the state of several lines in the given buffer.")


  (toggle-line [buffer tag]

    "Toggles the state of a single line in the given buffer.")


  (toggle-lines [buffer]
                [buffer tags]

    "Toggles the state of several lines in the given buffer."))




(defn- -buffer

  ;; Produces a buffer associated with a bunch of tags.

  [tag->GpioLine]

  (let [gpio-buffer (GpioBuffer.)]
    (reify

      IBuffer

        (clear-lines [this]
          (.clear gpio-buffer)
          this)


        (get-line [_ tag]
          (.get gpio-buffer
                (-get-gpio-line tag->GpioLine
                                tag)))


        (get-lines [this]
          (get-lines this
                     (keys tag->GpioLine)))


        (get-lines [this tags]
          (reduce (fn line-state [tag->state tag]
                    (assoc tag->state
                           tag
                           (get-line this
                                     tag)))
                  {}
                  tags))


        (set-line [this tag state]
          (.set gpio-buffer
                (-get-gpio-line tag->GpioLine
                                tag)
                state)
          this)


        (set-lines [this tag->state]
          (doseq [[tag state] tag->state]
            (set-line this
                      tag
                      state))
          this)


        (toggle-line [this tag]
          (set-line this
                    tag
                    (not (get-line this
                                   tag)))
          this)


        (toggle-lines [this]
          (toggle-lines this
                        (keys tag->GpioLine)))


        (toggle-lines [this tags]
          (doseq [tag tags]
            (toggle-line this
                         tag))
          this)


     IPure

       (-raw-type [_]
         gpio-buffer)
     )))




;;;;;;;;;;


(defprotocol IHandle

  "IO using a GPIO handle and a associated buffer.

   Reading or writing the state of lines happens virtually at once for all of them. Whether it happens exactly
   at the same time depends on the underlying driver and this fact is opaque. In any case, driving several
   lines using a single handle is more efficient than using a handle for each line."

  (read [handle buffer]

    "Using a handle, reads the current state of lines into the given buffer.")


  (write [handle buffer]

    "Using a handle, writes the current state of lines using the given buffer."))




(defn handle

  "Given a GPIO device, requests a handle for one or several lines which can then be used to read and/or write
   the state of lines.
  
   Implements `IHandle`.


   `line-number->line-options` is a map where keys are line numbers and values are maps which may contain :

     ::state
     ::tag
       Cf. Namespace description


   `handle-options` is an optional map which may contain :

     ::active-low?
     ::consumer
     ::direction
     ::open-drain?
     ::open-source?
       Cf. Namespace description
  

   Ex. (handle some-device
               {17 {::tag :red-led}
                27 {::tag   :green-led
                    ::state true}}
               {::direction :output})"

  (^AutoCloseable

   [device lines-number->line-options]

   (handle device
           lines-number->line-options
           nil))


  (^AutoCloseable
    
   [^GpioDevice device line-number->line-options handle-options]

   (let [req           (GpioHandleRequest.)
         tag->GpioLine (reduce-kv (fn add-tag [tag->GpioLine line-number line-options]
                                    (assoc tag->GpioLine
                                           (get line-options
                                                ::tag
                                                line-number)
                                           (if (::state line-options)
                                             (.addLine req
                                                       line-number
                                                       true)
                                             (.addLine req
                                                       line-number))))
                                  {}
                                  line-number->line-options)]
     (some->> (::consumer handle-options)
              (.setConsumer req))
     (.setFlags req
                (-flags handle-options))
     (let [gpio-handle (.requestHandle device
                                       req)]
       (reify

         AutoCloseable

           (close [_]
             (.close gpio-handle))


        IBuffereable

          (buffer [_]
            (-buffer tag->GpioLine))


        IHandle

          (read [_ buffer]
            (.read gpio-handle
                   (-raw-type buffer)))


          (write [_ buffer]
            (.write gpio-handle
                    (-raw-type buffer)))
          )))))




;;;;;;;;;;


(defn- -event-handle

  ;; Requests a GpioEventHandle.

  ^GpioEventHandle

  [^GpioDevice device line-number event-handle-options]

  (.requestEvent device
                 (let [req (GpioEventRequest. line-number
                                              (condp identical?
                                                     (or (get event-handle-options
                                                              ::edge-detection)
                                                         (get defaults
                                                              ::edge-detection))
                                                :rising             GpioEdgeDetection/RISING
                                                :falling            GpioEdgeDetection/FALLING
                                                :rising-and-falling GpioEdgeDetection/RISING_AND_FALLING)
                                              (-flags event-handle-options))]

                   (some->> (::consumer event-handle-options)
                            (.setConsumer req))
                   req)))




(defprotocol IWatcher

  "IO using a watcher."

  (poll [watcher buffer tag]

    "Using a watcher, reads the current state of a line.")


  (event [watcher]
         [watcher timeout-ms]

    "Using a watcher, efficiently blocks until the state of one of the monitored inputs switches to a relevant
     one or the timeout elapses.
    
     A timeout of -1 will block forever until something happens."))




(defn- -close-watcher-resources

  ;; Closes all resources related to a watcher.

  [^GpioEventWatcher gpio-watcher tag->event-handle]

  (close gpio-watcher)
  (doseq [[_ event-handle] tag->event-handle]
    (close event-handle)))




(defn watcher

  "Given a GPIO device, produces a watcher which can then be used to efficiently monitor inputs for changes or poll
   their current values.
  
   Implements `IWatcher`.


   `line-number->watch-options` is a map where keys are line numbers and values are maps which may contain :

     ::active-low?
     ::consumer
     ::direction
     ::open-drain?
     ::open-source?
     ::state
     ::tag
       Cf. Namespace description


   Ex. (watcher some-device
                {18 {::tag       :left-button
                     ::direction :input}
                 23 {::tag       :right-button
                     ::direction :output}})"

  ^AutoCloseable

  [^GpioDevice device line-number->watch-options]

  (let [gpio-watcher      (GpioEventWatcher.)
        tag->event-handle (reduce-kv (fn event-handle [tag->event-handle line-number watch-options]
                                       (try
                                         (assoc tag->event-handle
                                                (if (contains? watch-options
                                                               ::tag)
                                                  (::tag watch-options)
                                                  line-number)
                                                (-event-handle device
                                                               line-number
                                                               watch-options))
                                         (catch Throwable e
                                           (-close-watcher-resources gpio-watcher
                                                                     tag->event-handle)
                                           (throw e))))
                                     {}
                                     line-number->watch-options)
        line-number->tag (reduce-kv (fn add-event-handle [line-number->tag tag ^GpioEventHandle event-handle]
                                      (try
                                        (let [line-number (.-lineNumber (.getLine event-handle))]
                                          (.addHandle gpio-watcher
                                                      event-handle
                                                      line-number)
                                          (assoc line-number->tag
                                                 line-number
                                                 tag))
                                        (catch Throwable e
                                          (-close-watcher-resources gpio-watcher
                                                                    tag->event-handle)
                                          (throw e))))
                                    {}
                                    tag->event-handle)
        gpio-event       (GpioEvent.)]
    (reify

      AutoCloseable

        (close [_]
          (-close-watcher-resources gpio-watcher
                                    tag->event-handle))


      IBuffereable

        (buffer [_]
          (-buffer (reduce-kv (fn gpio-line [tag->GpioLine tag ^GpioEventHandle event-handle]
                                (assoc tag->GpioLine
                                       tag
                                       (.getLine event-handle)))
                              {}
                              tag->event-handle)))


      IWatcher

        (poll [_ buffer tag]
          (if (contains? tag->event-handle
                         tag)
            (do
              (.read ^GpioEventHandle (get tag->event-handle
                                           tag)
                     (-raw-type buffer))
              (get-line buffer
                        tag))
            (throw (IllegalArgumentException. (format "GPIO watcher is not handling tag = %s"
                                                      tag)))))


        (event [this]
          (event this
                 -1))


        (event [_ timeout-ms]
          (when (.waitForEvent gpio-watcher
                               gpio-event
                               timeout-ms)
            {::edge           (if (.isRising gpio-event)
                                :rising
                                :falling)
             ::nano-timestamp (.getNanoTimestamp gpio-event)
             ::tag            (get line-number->tag
                                   (.getId gpio-event))}))
      )
    ))
