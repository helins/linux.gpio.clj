(defproject dvlopt/linux-gpio
            "0.0.0"

  :description  "Use the standard Linux GPIO api from Clojure"
  :url          "https://github.com/dvlopt/linux-gpio.clj"
  :license      {:name "Eclipse Public License"
                 :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[dvlopt/void          "0.0.0"]
                 [org.clojure/clojure  "1.9.0"]
                 [io.dvlopt/linux-gpio "1.0.0"]]
  :profiles     {:dev {:source-paths ["dev"
                                      "examples"]
                       :main         user
                       :dependencies [[org.clojure/test.check "0.10.0-alpha2"]
                                      [criterium              "0.4.4"]]
                       :plugins      [[venantius/ultra "0.5.2"]
                                      [lein-codox      "0.10.3"]]
                       :codox        {:output-path  "doc/auto"
                                      :source-paths ["src"]}
                       :repl-options {:timeout 180000}
                       :global-vars  {*warn-on-reflection* true}}})

