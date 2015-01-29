(ns lumber_calculator.core
  (:require [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]))

(defonce state (atom {
  :sizes [{:nominal [1.0 2.0] :actual [0.75 1.5]}
          {:nominal [1.0 3.0] :actual [0.75 2.5]}
          {:nominal [1.0 4.0] :actual [0.75 3.5]}
          {:nominal [1.0 5.0] :actual [0.75 4.5]}
          {:nominal [1.0 6.0] :actual [0.75 5.5]}
          {:nominal [1.0 7.0] :actual [0.75 6.25]}
          {:nominal [1.0 8.0] :actual [0.75 7.25]}
          {:nominal [1.0 10.0] :actual [0.75 9.25]}
          {:nominal [1.0 12.0] :actual [0.75 11.25]}

          {:nominal [1.25 4.0] :actual [1.0 3.5]}
          {:nominal [1.25 6.0] :actual [1.0 5.5]}
          {:nominal [1.25 8.0] :actual [1.0 7.25]}
          {:nominal [1.25 10.0] :actual [1.0 9.25]}
          {:nominal [1.25 12.0] :actual [1.0 11.25]}

          {:nominal [1.5 4.0] :actual [1.25 3.5]}
          {:nominal [1.5 6.0] :actual [1.25 5.5]}
          {:nominal [1.5 8.0] :actual [1.25 7.25]}
          {:nominal [1.5 10.0] :actual [1.25 9.25]}
          {:nominal [1.5 12.0] :actual [1.25 11.25]}

          {:nominal [2.0 2.0] :actual [1.5 1.5]}
          {:nominal [2.0 4.0] :actual [1.5 3.5]}
          {:nominal [2.0 6.0] :actual [1.5 5.5]}
          {:nominal [2.0 8.0] :actual [1.5 7.25]}
          {:nominal [2.0 10.0] :actual [1.5 9.25]}
          {:nominal [2.0 12.0] :actual [1.5 11.25]}

          {:nominal [3.0 6.0] :actual [2.5 5.5]}

          {:nominal [4.0 4.0] :actual [3.5 3.5]}
          {:nominal [4.0 6.0] :actual [3.5 5.5]}]
}))

(defn widget [data owner]
  (reify
    om/IRender
    (render [this]
      (html [:h1 (:text data)]))))

(om/root widget {:text "Hello, world!"}
         {:target (.querySelector js/document "main")})
