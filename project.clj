(defproject lumber_calculator "0.1.0-SNAPSHOT"
  :description "Determine the smallest amount of lumber you can buy to fulfill a project's requirements."
  :url "https://github.com/jasontbradshaw/lumber-calculator"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2740"]]

  :node-dependencies [[source-map-support "0.2.8"]]

  :plugins [[lein-cljsbuild "1.0.4"]
            [lein-npm "0.4.0"]]

  :source-paths ["src" "target/classes"]

  :clean-targets ["out/lumber_calculator" "lumber_calculator.js" "lumber_calculator.min.js"]

  :cljsbuild {
    :builds [{:id "dev"
              :source-paths ["src"]
              :compiler {
                :main lumber_calculator.core
                :output-to "out/lumber_calculator.js"
                :output-dir "out"
                :optimizations :none
                :cache-analysis true
                :source-map true}}
             {:id "release"
              :source-paths ["src"]
              :compiler {
                :main lumber_calculator.core
                :output-to "out-adv/lumber_calculator.min.js"
                :output-dir "out-adv"
                :optimizations :advanced
                :pretty-print false}}]})
