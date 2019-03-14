(defproject keechma/forms "0.1.3"
  :description "Keechma Forms library allows you to build forms (using the Reagent library) that have a great UX."
  :url "http://github.com/keechma/forms"
  :license {:name "MIT"}

  :min-lein-version "2.6.1"
  
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/clojurescript "1.10.520"]
                 [re-frame "0.10.6"]
                 [org.clojure/core.async "0.4.490"
                  :exclusions [org.clojure/tools.reader]]]
  
  :plugins [[lein-figwheel "0.5.18"]
            [lein-codox "0.9.3"]
            [lein-cljsbuild "1.1.7" :exclusions [[org.clojure/clojure]]]]

  :source-paths ["src"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]
  
  :codox {:language :clojurescript
          :metadata {:doc/format :markdown}
          :namespaces [forms.core forms.dirty forms.validator]}
  
  :profiles {:test {:dependencies [[lein-doo "0.1.6"]
                                   [day8.re-frame/test "0.1.5"]]
                    :plugins [[lein-doo "0.1.6"]]}}

  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src"]

                ;; If no code is to be run, set :figwheel true for continued automagical reloading
                :figwheel {:on-jsload "forms.core/on-js-reload"}

                :compiler {:main forms.core
                           :asset-path "js/compiled/out"
                           :output-to "resources/public/js/compiled/forms.js"
                           :output-dir "resources/public/js/compiled/out"
                           :source-map-timestamp true}}
               ;; This next build is an compressed minified build for
               ;; production. You can build this with:
               ;; lein cljsbuild once min
               {:id "min"
                :source-paths ["src"]
                :compiler {:output-to "resources/public/js/compiled/forms.js"
                           :main forms.core
                           :optimizations :advanced
                           :pretty-print false}}
               {:id "test"
                                          :source-paths ["src", "test"]
                                          :compiler {:output-to
                                                     "resources/public/js/compiled/test.js"
                                                     :optimizations :none
                                                     :main forms.test.test}}]}

  :figwheel {;; :http-server-root "public" ;; default and assumes "resources"
             ;; :server-port 3449 ;; default
             ;; :server-ip "127.0.0.1"

             :css-dirs ["resources/public/css"] ;; watch and update CSS

             ;; Start an nREPL server into the running figwheel process
             ;; :nrepl-port 7888

             ;; Server Ring Handler (optional)
             ;; if you want to embed a ring handler into the figwheel http-kit
             ;; server, this is for simple ring servers, if this
             ;; doesn't work for you just run your own server :)
             ;; :ring-handler hello_world.server/handler

             ;; To be able to open files in your editor from the heads up display
             ;; you will need to put a script on your path.
             ;; that script will have to take a file path and a line number
             ;; ie. in  ~/bin/myfile-opener
             ;; #! /bin/sh
             ;; emacsclient -n +$2 $1
             ;;
             ;; :open-file-command "myfile-opener"

             ;; if you want to disable the REPL
             ;; :repl false

             ;; to configure a different figwheel logfile path
             ;; :server-logfile "tmp/logs/figwheel-logfile.log"
             })
