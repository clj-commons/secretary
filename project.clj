(defproject secretary "0.2.0"
  :description "A client-side router for ClojureScript."
  :url "https://github.com/gf3/secretary"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-1896"]]
  :plugins [[lein-cljsbuild "0.3.3"]] 
  :profiles {:dev {:dependencies [[com.cemerick/piggieback "0.1.0"]]
                   :plugins [[com.cemerick/austin "0.1.1"]]
                   :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}}
  :cljsbuild {:builds [{:source-paths ["src/"]
                        :compiler {:output-to "resources/secretary.js"
                                   :optimizations :whitespace
                                   :pretty-print true}}]})
