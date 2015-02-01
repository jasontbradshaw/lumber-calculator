(ns lumber_calculator.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.string :as string]
            [cljs.core.async :refer [put! chan <!]]
            [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]))

(defonce app (atom {
  ;; available lumber sizes for use when building components
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

  ;; components the user has added to their project
  :components []
}))

;; FIXME: remove this!
(add-watch app :debug-watcher
           (fn [_ _ _ _]
             (.log js/console (clj->js @app))))

;; the root element of our application
(defonce root (.querySelector js/document "main"))

;; turn a float between 0 and 1 (exclusive of both) into a pretty fraction.
;; supports fractions in increments of 1/8 only, otherwise returns the number
;; as a string.
(defn pretty-float [x]
  (cond (= x (/ 1 8.0)) "⅛"
        (= x (/ 2 8.0)) "¼"
        (= x (/ 3 8.0)) "⅜"
        (= x (/ 4 8.0)) "½"
        (= x (/ 5 8.0)) "⅝"
        (= x (/ 6 8.0)) "¾"
        (= x (/ 7 8.0)) "⅞"
        :else (str x)))

;; generates and returns a new (hopefully) unique string id of the given length
;; (default 16 characters). easily-confused characters ('i', 'l', '1', etc.)
;; are excluded from the generated ids.
(def ^:const id-alphabet "abcdefghjkmnopqrstuvwxyz023456789")
(defn generate-id
  ([] (generate-id 16))
  ([length] (apply str (repeatedly length #(rand-nth id-alphabet)))))

;; turn a size like {:nominal [1.5 4.0]} into '1½" × 4"'
(defn pretty-size [size]
  (let [height (get (:nominal size) 0)
        width (get (:nominal size) 1)
        height-whole (.floor js/Math height)
        height-frac (- height height-whole)
        width-whole (.floor js/Math width)
        width-frac (- width width-whole)]
    (string/join (flatten [height-whole (if (= 0 height-frac)
                                          []
                                          (pretty-float height-frac))
                           "\""
                           " × "
                           width-whole (if (= 0 width-frac)
                                         []
                                         (pretty-float width-frac))
                           "\""]))))

;; give a size an id value
(defn size->id [size]
  (string/join "x" (:nominal size)))

;; returns a blank component using the given sizes (default global sizes)
(defn new-component [sizes]
  {:id (generate-id)
   :size (get sizes 0)
   :length 0
   :name ""
   :count 1})

;; a single project component piece
(defcomponent component-view [component owner]
  (render-state [this {:keys [update delete sizes]}]
    (html [:li {:id (:id component)
                :class "component"}
           [:select {:class "component-sizes"
                     :value (size->id (:size component))
                     :on-input (fn [e]
                                 (let [v (.. e -target -value)]
                                   (.log js/console v)
                                   (put! update
                                         [component
                                          {:size (some #(if (= v (size->id %)) %)
                                                       sizes)}])))}
            ;; list all the possible stock sizes
            (map #(vec [:option {:value (size->id %)}
                        (pretty-size %)])
                 sizes)]
           [:input {:class "component-name"
                    :type "text"
                    :name "name"
                    :placeholder "Component Name (Optional)"
                    :value (:name component)
                    :on-input #(put! update
                                     [component
                                      {:name (string/triml (.. % -target -value))}])
                    :on-blur #(put! update
                                    [component
                                     {:name (string/trim (.. % -target -value))}])}]
           [:input {:class "component-length"
                    :type "number"
                    :name "length"
                    :value (:length component)
                    :on-input #(put! update
                                     [component
                                      {:length (.parseFloat
                                                 js/window
                                                 (.. % -target -value)
                                                 10)}])}]
           [:input {:class "component-count"
                    :type "number"
                    :min 0 :step 1
                    :name "count"
                    :value (:count component)
                    :on-input #(put! update
                                     [component
                                      {:count (.parseFloat
                                                js/window
                                                (.. % -target -value)
                                                10)}])}]
           [:button {:on-click #(put! delete component)} "Delete"]])))

(defcomponent components-view [app owner]
  (init-state [_]
    {:create (chan)
     :update (chan)
     :delete (chan)})

  (will-mount [_]
    ;; listen for events that modify the components list
    (let [create (om/get-state owner :create)
          update (om/get-state owner :update)
          delete (om/get-state owner :delete)]
      ;; create
      (go (loop []
            (let [component (<! create)]
              (om/transact! app :components
                            (fn [components]
                              ;; add a new blank component onto the list
                              (conj components component)))
              (recur))))
      ;; update
      (go (loop []
            (let [[component attrs] (<! update)]
              (om/transact! app :components
                            (fn [components]
                              (vec (map #(if (= % component)
                                           (merge % attrs)
                                           %) components))))
              (recur))))
      ;; delete
      (go (loop []
            (let [component (<! delete)]
              (om/transact! app :components
                            (fn [components]
                              (vec (remove #(= component %) components))))
              (recur))))))

  (render-state [this {:keys [create update delete]}]
    (html [:ul {:class "components"}
           (om/build-all component-view (:components app)
                         {:init-state {:update update
                                       :delete delete
                                       :sizes (:sizes app)}})
           [:button {:on-click #(put! create (new-component (:sizes app)))}
            "Add Component"]])))

(om/root components-view app {:target root})
