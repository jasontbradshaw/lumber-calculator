(defproject lumber-calculator "0.1.0-SNAPSHOT"
  :description "Determine the smallest amount of lumber you can buy to fulfill a project's requirements."
  :url "https://github.com/jasontbradshaw/lumber-calculator"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2740"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.omcljs/om "0.8.7"]
                 [sablono "0.3.1"]
                 [prismatic/om-tools "0.3.10"]]

  :plugins [[lein-cljsbuild "1.0.4"]]

  :source-paths ["src" "target/classes"]

  :clean-targets ["out/lumber_calculator" "out/lumber_calculator.js"]

  :cljsbuild {
    :builds [{:id "lumber-calculator"
              :source-paths ["src"]
              :compiler {
                :output-to "out/lumber_calculator.js"
                :output-dir "out"
                :optimizations :none
                :cache-analysis true
                :source-map true}}]})
