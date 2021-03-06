(ns lumber_calculator.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.string :as string]
            [cljs.core.async :refer [put! chan <!]]
            [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]))

;; simple functions for dealing with our size maps and dimension vectors
(def height first)
(def width second)

(defn nominal-height [size] (-> size :nominal height))
(defn nominal-width [size] (-> size :nominal width))
(defn actual-height [size] (-> size :actual height))
(defn actual-width [size] (-> size :actual width))

(defn feet->inches [f] (* f 12.0))
(defn inches->feet [i] (/ i 12.0))

(defonce app (atom {
  ;; available lumber sizes for use when building components, sorted by height
  ;; then width.
  :sizes (sorted-set-by #(if (= (nominal-height %) (nominal-height %2))
                           (< (nominal-width %) (nominal-width %2))
                           (< (nominal-height %) (nominal-height %2)))
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

  ;; available lumber lengths in inches
  :lengths (sorted-set
                    (feet->inches 6)
                    (feet->inches 8)
                    (feet->inches 10)
                    (feet->inches 12)
                    (feet->inches 14)
                    (feet->inches 16)
                    (feet->inches 18)
                    (feet->inches 20)
                    (feet->inches 22)
                    (feet->inches 24))

  ;; components the user has added to their project
  :components []
}))

;; FIXME: remove this!
(add-watch app :debug-watcher
           (fn [_ _ _ _]
             (.log js/console (clj->js @app))))

;; the root element of our application
(defonce root (.querySelector js/document "main"))

(defn longest
  "Returns the longest component in a sequence of components, or nil if the
  sequence is empty."
  ([components]
   (longest (first components) (rest components)))
  ([longest-so-far xs]
   (let [candidate (first xs)
         remaining (rest xs)]
     (cond
       (nil? longest-so-far) nil
       (empty? remaining) longest-so-far
       (< (:length longest-so-far) (:length candidate)) (recur candidate remaining)
       :else (recur longest-so-far remaining)))))

(defn shortest-stock-fit
  "Returns the shortest stock length that can contain the given component."
  [component]
  (some #(<= (:length component) %) (:lengths @app)))

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

(defn pretty-size-nominal
  "Turn a size like '{:nominal [1.5 4.0]}' into '1½\" × 4\"'"
  [size]
  (let [height (nominal-height size)
        width (nominal-width size)
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
  "Returns blank component map data with the given size."
  [size]
  {:id (generate-id)
   :size size
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
  (render-state [this {:keys [delete sizes]}]
    (html [:li {:id (:id component)
                :class "component"}
           [:select {:name "component-sizes"
                     :value (size->id (:size component))
                     :on-input (handle-update-size component sizes)}
            ;; list all the possible stock sizes
            (map #(vec [:option {:value (size->id %)}
                        (pretty-size-nominal %)])
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
           [:button {:on-click #(put! create (new-component (-> app :sizes first)))}
            "Add Component"]])))

(om/root components-view app {:target root})
