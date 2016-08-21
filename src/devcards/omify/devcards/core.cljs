(ns omify.devcards.core
  (:require [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [goog.object :as gobj]
            [omify.core :as omfy :refer [omify! omify]]
            [devcards.core :as dc :refer-macros [defcard dom-node]]
            [cljsjs.recharts]))

;; =============================================================================
;; Setup

(defn init! []
  (enable-console-print!)
  (dc/start-devcard-ui!))

(def update-atom (atom {}))

(def ExampleComponent
  (js/React.createClass
    #js {:getInitialState
         (fn []
           #js {:foo 42})
         :clicked
         (fn []
           (this-as this
             (.setState this #js {:foo 43})))
         :componentWillMount
         (fn []
           (this-as this
             (swap! update-atom assoc
               :componentWillMount (.. this -props))))
         :componentDidMount
         (fn []
           (this-as this
             (swap! update-atom assoc
               :componentDidMount (.. this -props))))
         :componentWillReceiveProps
         (fn [next-props]
           (this-as this
             (swap! update-atom assoc
               :componentWillReceiveProps next-props)))
         :shouldComponentUpdate
         (fn [next-props next-state]
           (swap! update-atom assoc
             :shouldComponentUpdate
             {:props next-props
              :state next-state})
           true)
         :componentWillUpdate
         (fn [next-props next-state]
           (swap! update-atom assoc
             :componentWillUpdate {:props next-props
                                   :state next-state}))
         :componentDidUpdate
         (fn [prev-props prev-state]
           (swap! update-atom assoc
             :componentDidUpdate {:props prev-props
                                  :state prev-state}))
         :componentWillUnmount
         (fn []
           (this-as this
             (swap! update-atom assoc
               :componentWillUnmount (.. this -props))))
         :render
         (fn []
           (this-as this
             (js/React.DOM.div #js {:onClick (gobj/get this "clicked")}
               (.. this -props -message) " "
               (gobj/get (.. this -state) "foo"))))}))

(defcard omifyed
  (let [omified (omify ExampleComponent
                  Object
                  (render [this]
                    (js/React.DOM.div #js {:onClick (.. this -clicked)}
                      "modified render "
                      (.. this -props -message))))]
    (dom-node
      (fn [_ node]
        (js/ReactDOM.render
          (js/React.createElement omified #js {:message "Hi!"})
          node)))))

(omify! ExampleComponent)

(def reconciler
  (om/reconciler {:state (atom #js {:message "Hello, world!"})}))

(defui Root
  Object
  (render [this]
    ((omfy/factory ExampleComponent) (om/props this))))

(defcard omify!ed
  (dom-node
    (fn [_ node]
      (om/add-root! reconciler Root node))))


;; =============================================================================
;; Recharts example

(def LineChart js/Recharts.LineChart)

(omify! LineChart
  static om/IQuery
  (query [this]
    [:width :height :data]))

(def line-chart (omfy/factory LineChart))

(def Line js/Recharts.Line)

(def LineComponent
  ;; Note: we could use `omify!` directly just like we did in LineChart, but
  ;; I also wanted to demonstrate the `omify` construct, which doesn't mutate
  ;; its argument. `omify!` and `omify` are akin to `specify!` and `specify`, respectively.
  (omify Line
    static om/IQuery
    (query [this]
      [:type :dataKey :stroke])))

(def line (omfy/factory LineComponent))

(defui RechartsRoot
  static om/IQuery
  (query [this]
    [{:chart (om/get-query LineChart)}
     {:line (om/get-query LineComponent)}])
  Object
  (render [this]
    (let [{:keys [chart] chart-line :line} (om/props this)]
      (dom/div nil
        (line-chart chart
          (line chart-line))))))

(defn recharts-read
  [{:keys [state]} key _]
  {:value (get @state key)})

(def recharts-reconciler
  (om/reconciler
    {:state (atom {:chart {:width 600
                           :height 300
                           :data [{:name "Page A" :uv 400 :pv 2400 :amt 2400}
                                  {:name "Page B" :uv 300 :pv 4567 :amt 2400}
                                  {:name "Page C" :uv 300 :pv 1398 :amt 2400}
                                  {:name "Page D" :uv 200 :pv 9800 :amt 2400}
                                  {:name "Page E" :uv 278 :pv 3908 :amt 2400}
                                  {:name "Page F" :uv 189 :pv 4800 :amt 2400}]}
                   :line {:type "monotone"
                          :dataKey "uv"
                          :stroke "#8884d8"}})
     :parser (om/parser {:read recharts-read})}))

(defcard recharts-omifyied
  (dom-node
    (fn [_ node]
      (om/add-root! recharts-reconciler RechartsRoot node))))

;; TODO:
;; - test a component with ident
