(ns svged.core
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.pprint :refer [pprint]]
            [re-com.core :as re-com]
            [svged.svg-geometry :refer [x y] :as g]
            [goog.events :as events])
  (:import [goog.events EventType]))

(enable-console-print!)

;; COMMON

(defn maxkey [m]
  (if m
    (apply max (keys m))
    0))

;;http://stackoverflow.com/questions/14488150/how-to-write-a-dissoc-in-command-for-clojure
(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

(defn get-bcr [dom-root]
  (-> dom-root
      reagent/dom-node
      .getBoundingClientRect))

(defn drag-move-fn [on-drag]
  (fn [evt]
    (on-drag (.-clientX evt) (.-clientY evt))))

(defn drag-end-fn [drag-move drag-end on-end]
  (fn [evt]
    (events/unlisten js/window EventType.MOUSEMOVE drag-move)
    (events/unlisten js/window EventType.MOUSEUP @drag-end)
    (on-end (.-clientX evt) (.-clientY evt))))

(defn drag-start-fn [drag-move drag-start on-start]
  (fn [evt]
    (events/unlisten js/window EventType.MOUSEDOWN @drag-start)
    (on-start (.-clientX evt) (.-clientY evt))))

(defn dragging
  ([on-drag] (dragging on-drag (fn []) (fn [])))
  ([on-drag on-start on-end]
   (let [drag-move (drag-move-fn on-drag)
         drag-start-atom (atom nil)
         drag-start (drag-start-fn drag-move drag-start-atom on-start)
         drag-end-atom (atom nil)
         drag-end (drag-end-fn drag-move drag-end-atom on-end)]
     (reset! drag-start-atom drag-start)
     (reset! drag-end-atom drag-end)
     (events/listen js/window EventType.MOUSEDOWN drag-start)
     (events/listen js/window EventType.MOUSEMOVE drag-move)
     (events/listen js/window EventType.MOUSEUP drag-end))))

(defn delete-object [id data]
  (swap! data dissoc-in [:svg id]))

(defn moving-object [id svg-root data width height]
  (fn [a b]
    (let [x (get-in @data [:svg id :params :x])
          y (get-in @data [:svg id :params :y])
          a (- a (-> (.-left (get-bcr svg-root))))
          b (- b (-> (.-top (get-bcr svg-root))))]
      (swap! data assoc-in [:svg id :params :x] a)
      (swap! data assoc-in [:svg id :params :y] b))))

(defn resizing-rect [id svg-root data psn-kw]
  (fn [a b]
    (let [a (int (- a (-> (.-left (get-bcr svg-root)))))
          b (int (- b (-> (.-top (get-bcr svg-root)) )))
          width (get-in @data [:svg id :params :width])
          height (get-in @data [:svg id :params :height])
          x (get-in @data [:svg id :params :x])
          y (get-in @data [:svg id :params :y])
          rotated-x-top (get-in @data [:svg id :params :rotated-x-top])
          rotated-y-top (get-in @data [:svg id :params :rotated-y-top])
          x-bside (- a (or rotated-x-top x))
          x-aside (.abs js/Math (- b (or rotated-y-top y)))]
      (swap! data assoc-in [:svg id :params :width]
             (condp = psn-kw
               :top width
               :right (int (.sqrt js/Math (+ (* x-aside x-aside) (* x-bside x-bside))))
               :bottom width
               :left (- (+ x width) a)))
      (swap! data assoc-in [:svg id :params :height]
             (condp = psn-kw
               :top (- (+ y height) b)
               :right height
               :bottom (+ height (- b (+ y height)))
               :left height))
      (swap! data assoc-in [:svg id :params :x]
             (condp = psn-kw
               :top x
               :right x
               :bottom x
               :left a))
      (swap! data assoc-in [:svg id :params :y]
             (condp = psn-kw
               :top b
               :right y
               :bottom y
               :left y)))))

(defn rotating-rect [id svg-root data]
  (fn [a b]
    (let [a (int (- a (-> (.-left (get-bcr svg-root)))))
          b (int (- b (-> (.-top (get-bcr svg-root)) )))
          width (get-in @data [:svg id :params :width])
          height (get-in @data [:svg id :params :height])
          x (get-in @data [:svg id :params :x])
          y (get-in @data [:svg id :params :y])
          center-y (+ y (/ height 2))
          center-x (+ x (/ width 2))
          rotation-rads (.atan2 js/Math
                                (- b center-y)
                                (- a center-x))
          rotation-degrees (int (* rotation-rads
                                   (/ 180 (.-PI js/Math))))
          rotated-x-top (+
                         (- (* (- x center-x) (.cos js/Math rotation-rads))
                            (* (- y center-y) (.sin js/Math rotation-rads)))
                         center-x)
          rotated-y-top (+
                         (+ (* (- x center-x) (.sin js/Math rotation-rads))
                            (* (- y center-y) (.cos js/Math rotation-rads)))
                         center-y)]

      (swap! data assoc-in [:svg id :params :rotated-x-top] (int rotated-x-top))
      (swap! data assoc-in [:svg id :params :rotated-y-top] (int rotated-y-top))
      (swap! data assoc-in [:svg id :params :rotation-degrees] rotation-degrees)
      (swap! data assoc-in [:svg id :params :center-x] center-x)
      (swap! data assoc-in [:svg id :params :center-y] center-y)

      (swap! data assoc-in [:svg id :params :transform]
             (str "rotate("rotation-degrees", "center-x" "center-y")")))))

;; COMPS

(defn props-edit [id data item-data]
  (fn [_ _ item-data]
    [:div {:style {:border "solid 1px #AEAEAE"
                   :background "#FFF"
                   :padding "10px"}}
     (doall
      (map
       (fn [[k v]]
         ^{:key k}
         [re-com/h-box
          :gap "10px"
          :children
          [[re-com/label
            :style {:width "110px"}
            :label (str k)]
           [re-com/input-text
            :change-on-blur? false
            :model (str v)
            :on-change #(swap! data assoc-in [:svg id :params k] %)]]])
       item-data))]))

(defn svg-props-edit [data]
  (fn []
    [:div
     (map
      (fn [[attr v]]
        ^{:key attr}
        [re-com/v-box :gap "10px"
         :children
         [[re-com/label :style {:width "90px"} :label (str attr)]
          [:div
           (map
            (fn [[k v]]
              ^{:key k}
              [re-com/h-box :gap "10px"
               :children
               [[re-com/label :style {:width "50px"} :label (str k)]
                [:span (str v)]
                [re-com/slider
                 :model v
                 :max 1200
                 :width "300px"
                 :on-change #(swap! data assoc-in [:svg-params attr k] %)]]])
            v)]]])
      (:svg-params @data))]))

(defn circle [{:keys [r fill on-mouse-down-fn]} p]
  [:circle
   {:stroke "black"
    :stroke-width 2
    :fill (or fill "blue")
    :r r
    :on-mouse-down on-mouse-down-fn
    :cx (x p)
    :cy (y p)}])

(defn rect []
  (fn [{:keys [id x y width height transform svg-root move-fn data action]}]
    (let [x (int x)
          y (int y)
          width (int width)
          height (int height)]
      [:g
       {:transform transform}
       [:rect
        {:width width
         :height height
         :stroke "black"
         :fill "yellow"
         :x x
         :y y}]
       [:g
        (condp = action
          :adjust [:g
                   [circle {:r 7 :fill "violet" :on-mouse-down-fn #(dragging (moving-object id svg-root data width height))}
                    (g/point x y)]

                   [circle {:r 7 :on-mouse-down-fn #(dragging (resizing-rect id svg-root data :bottom))}
                    (g/point (+ x (/ width 2)) (+ y height))]

                   [circle {:r 7 :on-mouse-down-fn #(dragging (resizing-rect id svg-root data :top))}
                    (g/point (+ x (/ width 2)) y)]

                   [circle {:r 7 :on-mouse-down-fn #(dragging (resizing-rect id svg-root data :right))}
                    (g/point (+ x width) (+ y (- (/ height 2) 30)))]

                   [circle {:r 7 :on-mouse-down-fn #(dragging (resizing-rect id svg-root data :left))}
                    (g/point x (+ y (/ height 2)))]

                   [circle {:r 7 :fill "green" :on-mouse-down-fn #(dragging (rotating-rect id svg-root data))}
                    (g/point (+ x width) (+ y (/ height 2)))]]
          :delete [circle {:r 10 :fill "red" :on-mouse-down-fn #(delete-object id data)}
                   (g/point (+ x (/ width 2)) (+ y (/ height 2)))]
          [:switch
           [:foreignObject { :x (+ x 10) :y (+ y 10) :width "100" :height "30"}
            [:span ""]]])]])))


(defn svg-canvas [data action]
  (let [svg-root (reagent/current-component)]
    (fn []
      (let [svg [:svg {:width 1200 :height 1000
                       :style {:border "solid 1px #AEAEAE"}
                       :view-box (clojure.string/join " " (vals (get-in @data [:svg-params :view-box])))}
                 (doall
                  (map
                   (fn [[id {:keys [id comp params]}]]
                     ^{:key id}
                     [comp (merge
                            params
                            {:id id
                             :svg-root svg-root
                             :data data
                             :action @action})])
                   (:svg @data)))]]
        svg))))

(defn svg-tools [data action]
  (fn []
    [:div
     [:h3 "draw"]
     [:div {:on-click #(let [new-id (inc (maxkey (:svg @data)))]
                         (swap! data assoc-in [:svg new-id]
                                {:id new-id
                                 :comp rect
                                 :comp-key :rect
                                 :params
                                 {:x 400
                                  :y 200
                                  :width 100
                                  :height 100
                                  :transform ""}}))} "rect"]
     [:h3 "tools"]
     [re-com/v-box
      :gap "5px"
      :children
      [[:div {:on-click #(reset! action (if (= :adjust @action) nil :adjust))}
        (if (= :adjust @action) [:b "adjust"] "adjust")]
       [:div {:on-click #(reset! action (if (= :delete @action) nil :delete))}
        (if (= :delete @action) [:b "delete"] "delete")]
       [:div {:on-click #(swap! data assoc :svg nil)} "clear all"]]]]))

(defonce data (reagent/atom {:svg-params {:view-box {:min-x 0 :min-y 0 :width 1000 :height 1000}}}))
(defonce action (reagent/atom :adjust))

(defn svged []
  (fn []
    [re-com/h-box
     :gap "50px"
     :children
     [[re-com/h-split
       :panel-1 [svg-tools data action]
       :panel-2 [svg-canvas data action]
       :initial-split 10]
      [re-com/v-box
       :gap "30px"
       :children
       [[svg-props-edit data]
        [:div
         (doall
          (map
           (fn [[id mdata]]
             ^{:key id}
             [props-edit id data (:params mdata)])
           (:svg @data)))]]]]]))

(reagent/render-component [svged] (. js/document (getElementById "app")))

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
