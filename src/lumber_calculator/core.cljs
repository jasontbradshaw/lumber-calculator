(ns lumber_calculator.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.string :as string]
            [cljs.core.async :refer [put! chan <!]]
            [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]))

(defonce app (atom {
  ;; available lumber sizes for use when building components, sorted by height
  ;; then width.
  :sizes (sorted-set-by (fn [a b]
                          (let [a-height (-> a :nominal first)
                                a-width (-> a :nominal second)
                                b-height (-> b :nominal first)
                                b-width (-> b :nominal second)]
                            ;; sort first by height, then by width
                            (if-not (= a-height b-height)
                              (< a-height b-height)
                              (< a-width b-width))))
                    {:nominal [1.0 2.0] :actual [0.75 1.5]}
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
                    {:nominal [4.0 6.0] :actual [3.5 5.5]})

  ;; available lumber lengths in feet
  :lengths (sorted-set 6 8 10 12 14 16 18 20 22 24)

  ;; components the user has added to their project
  :components []
}))

;; FIXME: remove this!
(add-watch app :debug-watcher
           (fn [_ _ _ _]
             (.log js/console (clj->js @app))))

;; the root element of our application
(defonce root (.querySelector js/document "main"))

(defn pretty-fraction
  "Turn a float between 0 and 1 (exclusive of both) into a pretty fraction.
  Supports fractions in increments of 1/8 only, otherwise returns the number as
  a floating-point number string."
  [x]
  (cond (= x (/ 1 8.0)) "⅛"
        (= x (/ 2 8.0)) "¼"
        (= x (/ 3 8.0)) "⅜"
        (= x (/ 4 8.0)) "½"
        (= x (/ 5 8.0)) "⅝"
        (= x (/ 6 8.0)) "¾"
        (= x (/ 7 8.0)) "⅞"
        :else (str x)))

(def ^:const id-alphabet "abcdefghjkmnopqrstuvwxyz023456789")
(defn generate-id
  "Generates and returns a new random string id of the given length (default 16
  characters). Easily-confused characters ('i', 'l', '1', etc.) are excluded
  from the generated ids."
  ([] (generate-id 16))
  ([length] (apply str (repeatedly length #(rand-nth id-alphabet)))))

(defn pretty-size
  "Turn a size like {:nominal [1.5 4.0]} into \"1½\\\" × 4\\\"\""
  [size]
  (let [height (first (:nominal size))
        width (second (:nominal size))
        height-whole (.floor js/Math height)
        height-frac (- height height-whole)
        width-whole (.floor js/Math width)
        width-frac (- width width-whole)]
    (string/join (flatten [height-whole (if (= 0 height-frac)
                                          []
                                          (pretty-fraction height-frac))
                           "\""
                           " × "
                           width-whole (if (= 0 width-frac)
                                         []
                                         (pretty-fraction width-frac))
                           "\""]))))

(defn size->id
  "Create an identifying string id for a given size map."
  [size]
  (string/join "x" (:nominal size)))

(defn new-component
  "Returns a blank component map using the given sizes (default global sizes)."
  [sizes]
  {:id (generate-id)
   :size (first sizes)
   :length 0
   :name ""
   :count 1})

(defn merge-transact!
  "Does a transact! on a cursor, but instead of replacing the value, merges the
  result of running the function on the cursor into the existing cursor value."
  [cursor f]
  (om/transact!
    cursor
    (fn [data]
      (merge data (f data)))))

(defn handle-update-size [component sizes]
  (fn [e]
    (let [value (.. e -target -value)]
      (merge-transact!
        component
        (fn [data]
           {:size (some #(if (= value (size->id %)) %) sizes)})))))

(defn handle-update-float [component k]
  (fn [e]
    (merge-transact!
      component
      (fn [data]
        {k (.parseFloat js/window (.. e -target -value) 10)}))))

;; a single project component piece
(defcomponent component-view [component owner]
  (render-state [this {:keys [update delete sizes]}]
    (html [:li {:id (:id component)
                :class "component"}
           [:select {:name "component-sizes"
                     :value (size->id (:size component))
                     :on-input (handle-update-size component sizes)}
            ;; list all the possible stock sizes
            (map #(vec [:option {:value (size->id %)}
                        (pretty-size %)])
                 sizes)]
           [:input {:name "component-name"
                    :type "text"
                    :value (:name component)
                    :placeholder "Component Name (Optional)"
                    :on-input #(merge-transact!
                                 component
                                 (fn [data]
                                   {:name (string/triml
                                            (.. % -target -value))}))
                    :on-blur #(merge-transact!
                                 component
                                 (fn [data]
                                   {:name (string/trim
                                            (.. % -target -value))}))}]
           [:input {:name "component-length"
                    :type "number"
                    :value (:length component)
                    :min 0
                    :on-change (handle-update-float component :length)}]
           [:input {:name "component-count"
                    :type "number"
                    :value (:count component)
                    :min 0
                    :step 1
                    :on-input (handle-update-float component :count)}]
           [:button {:name "component-delete"
                     :on-click #(put! delete component)} "Delete"]])))

(defcomponent components-view [app owner]
  (init-state [_]
    {:create (chan)
     :delete (chan)})

  (will-mount [_]
    ;; listen for events that modify the components list
    (let [create (om/get-state owner :create)
          delete (om/get-state owner :delete)]
      ;; create
      (go (loop []
            (let [component (<! create)]
              (om/transact! app :components
                            (fn [components]
                              ;; add the new component onto the list
                              (conj components component)))
              (recur))))
      ;; delete
      (go (loop []
            (let [component (<! delete)]
              (om/transact! app :components
                            (fn [components]
                              (vec (remove #(= component %) components))))
              (recur))))))

  (render-state [this {:keys [create delete]}]
    (html [:ul {:class "components"}
           (om/build-all component-view (:components app)
                         {:init-state {:delete delete
                                       :sizes (:sizes app)}})
           [:button {:on-click #(put! create (new-component (:sizes app)))}
            "Add Component"]])))

(om/root components-view app {:target root})
