(defproject secretary "0.4.0-SNAPSHOT"
  :description "A client-side router for ClojureScript."
  :url "https://github.com/gf3/secretary"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-1896"]
                 [com.cemerick/clojurescript.test "0.0.4"]]
  :plugins [[lein-cljsbuild "0.3.3"]] 
  :hooks [leiningen.cljsbuild]
  :profiles {:dev {:dependencies [[com.cemerick/piggieback "0.1.0"]]
                   :plugins [[com.cemerick/austin "0.1.1"]]
                   :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}}
  :aliases {"run-tests" ["do" "clean," "cljsbuild" "test"]}
  :cljsbuild {:builds [{:source-paths ["src/" "test/"]
                        :compiler {:output-to "target/js/test.js"
                                   :optimizations :whitespace
                                   :pretty-print true}}]
              :test-commands {"unit-tests" ["phantomjs"  "runners/phantomjs.js" "target/js/test.js"]}})
