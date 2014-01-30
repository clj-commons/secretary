(defproject secretary "0.5.0-SNAPSHOT"
  :description "A client-side router for ClojureScript."
  :url "https://github.com/gf3/secretary"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2156"]]
  :plugins [[lein-cljsbuild "1.0.2"]
            [com.cemerick/clojurescript.test "0.2.2"]] 
  :hooks [leiningen.cljsbuild]
  :profiles {:dev {:plugins [[com.cemerick/austin "0.1.3"]]}}
  :aliases {"run-tests" ["do" "clean," "cljsbuild" "test"]}
  :cljsbuild {:builds [{:source-paths ["src/" "test/"]
                        :compiler {:output-to "target/js/test.js"
                                   :optimizations :whitespace
                                   :pretty-print true}}]
              :test-commands {"unit-tests" ["phantomjs" :runner "target/js/test.js"]}})
