(defproject clj-commons/secretary "1.2.4"
  :description "A client-side router for ClojureScript."
  :url "https://github.com/clj-commons/secretary"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}

  :dependencies
  [[org.clojure/clojure "1.6.0"]
   [org.clojure/clojurescript "0.0-2913" :scope "provided"]]

  :plugins
  [[lein-cljsbuild "1.0.5"]]

  :profiles
  {:dev {:source-paths ["dev/" "src/"]
         :dependencies
         [[com.cemerick/piggieback "0.1.6-SNAPSHOT"]
          [weasel "0.6.0"]]
         :plugins
         [[com.cemerick/clojurescript.test "0.2.3-SNAPSHOT"]]
         :repl-options
         {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}}

  :aliases
  {"run-tests" ["do" "clean," "cljsbuild" "once" "test"]
   "test-once" ["do" "clean," "cljsbuild" "once" "test"]
   "auto-test" ["do" "clean," "cljsbuild" "auto" "test"]}

  :cljsbuild
  {:builds [{:id "test"
             :source-paths ["src/" "test/"]
             :notify-command ["phantomjs" :cljs.test/runner "target/js/test.js"]
             :compiler {:output-to "target/js/test.js"
                        :optimizations :whitespace
                        :pretty-print true}}
            {:id "example-01"
             :source-paths ["src/" "examples/"]
             :compiler {:output-to "examples/example-01/example.js"
                        :optimizations :whitespace
                        :pretty-print true}}]})
