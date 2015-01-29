(ns lumber_calculator.core
  (:require [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]))

(defn widget [data owner]
  (reify
    om/IRender
    (render [this]
      (html [:h1 (:text data)]))))

(om/root widget {:text "Hello, world!"}
         {:target (.querySelector js/document "main")})
