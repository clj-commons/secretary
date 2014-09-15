(defproject secretary "1.2.2-SNAPSHOT"
  :description "A client-side router for ClojureScript."
  :url "https://github.com/gf3/secretary"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}

  :dependencies
  [[org.clojure/clojure "1.6.0"]]

  :profiles
  {:dev {:source-paths
         ["src" "dev"]

         :dependencies
         [[org.clojure/clojurescript "0.0-2322"]
          [weasel "0.4.0-SNAPSHOT"]
          [spellhouse/clairvoyant "0.0-33-g771b57f"]]


         :plugins
         [[lein-cljsbuild "1.0.3"]
          [com.cemerick/austin "0.1.3"]
          [com.cemerick/clojurescript.test "0.2.3-SNAPSHOT"]]

         :repl-options
         {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}}

  :aliases
  {"run-tests" ["do" "clean," "cljsbuild" "once" "test"]
   "test-once" ["do" "clean," "cljsbuild" "once" "test"]
   "auto-test" ["do" "clean," "cljsbuild" "auto" "test"]}

  :cljsbuild
  {:builds [{:id "dev"
             :source-paths ["dev/" "src/"]
             :compiler {:output-to "resources/public/secretary.js"
                        :output-dir "resources/public/out"
                        :optimizations :none
                        :source-map true
                        :pretty-print true}}

            {:id "test"
             :source-paths ["src/" "test/"]
             :notify-command ["phantomjs" :cljs.test/runner "target/js/test.js"]
             :compiler {:output-to "target/js/test.js"
                        :optimizations :whitespace
                        :pretty-print true}}

            {:id "example-01"
             :source-paths ["src/" "examples/example-01"]
             :compiler {:output-to "examples/example-01/example.js"
                        :optimizations :whitespace
                        :pretty-print true}}]})
