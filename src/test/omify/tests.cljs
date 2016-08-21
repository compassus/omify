(ns omify.tests
  (:require [cljs.test :refer [deftest testing is are use-fixtures async]]
            [om.next :as om :refer [defui]]
            [omify.core :as omfy :refer [omify! omify]]
            [goog.object :as gobj]
            [cljsjs.react]))

(def test-utils js/React.addons.TestUtils)

(def update-atom (atom {}))

(def ExampleComponent
  (js/React.createClass
    #js {:getInitialState
         (fn []
           (this-as this
             (swap! update-atom assoc
               :getInitialState (.. this -props -message))
             #js {:foo 42}))
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
             (js/React.DOM.div #js {:onClick (.. this -clicked)}
               (.. this -props -message)
               (.. this -state -foo))))}))

(deftest test-omify!-component
  (omify! ExampleComponent
    static om/Ident
    (ident [_ _]
      [:example/by-id 0])
    static om/IQuery
    (query [this]
      [:message]))
  (is (implements? om/IQuery ExampleComponent))
  (is (implements? om/Ident ExampleComponent))
  ;; reshaped?
  (is (.. ExampleComponent -prototype -isMounted))
  (let [msg "Hello, world!"
        shallow-renderer (.createRenderer test-utils)
        _ (.render shallow-renderer ((omfy/factory ExampleComponent) {:message msg}))
        c (.getRenderOutput shallow-renderer)]
    (is (= (first (.. c -props -children)) msg))
    (is (= (second (.. c -props -children)) 42))
    (.onClick (.. c -props))
    (.render shallow-renderer ((omfy/factory ExampleComponent) {:message msg}))
    ;; https://github.com/facebook/react/commit/941997
    (.componentDidMount (.getMountedInstance shallow-renderer))
    (.unmount shallow-renderer)
    (let [updated @update-atom]
      #_(is (= (-> updated :getInitialState (gobj/get "message")) msg))
      (is (= (-> updated :componentWillMount (gobj/get "message")) msg))
      (is (= (-> updated :componentDidMount (gobj/get "message")) msg))
      (is (= (-> updated :componentWillReceiveProps (gobj/get "message")) msg))
      (is (= (-> updated :shouldComponentUpdate :state (gobj/get "foo")) 43))
      (is (= (-> updated :shouldComponentUpdate :props (gobj/get "message")) msg))
      (is (= (-> updated :componentWillUpdate :state (gobj/get "foo")) 43))
      (is (= (-> updated :componentWillUpdate :props (gobj/get "message")) msg))
      (is (= (-> updated :componentDidUpdate :state (gobj/get "foo")) 42))
      (is (= (-> updated :componentDidUpdate :props (gobj/get "message")) msg))
      (is (= (-> updated :componentWillUnmount (gobj/get "message")) msg)))))

(def OtherExample
  (js/React.createClass
    #js {:getInitialState
         (fn []
           (this-as this
             #js {:foo 42}))
         :clicked
         (fn []
           (this-as this
             (.setState this #js {:foo 43})))
         :render
         (fn []
           (this-as this
             (js/React.DOM.div #js {:onClick (.. this -clicked)}
               (.. this -props -message)
               (.. this -state -foo))))}))

(deftest test-omify-component
  (let [omified (omify OtherExample
                  static om/Ident
                  (ident [_ _]
                    [:example/by-id 1])
                  static om/IQuery
                  (query [this]
                    [:message]))]
    (is (implements? om/IQuery omified))
    (is (implements? om/Ident omified))
    (is (not (identical? omified OtherExample)))
    (is (= (pr-str omified) "omify.tests/OtherExample"))))

(deftest test-omify-object-proto
  (let [omified (omify OtherExample
                  Object
                  (componentWillMount [_])
                  (render [this]
                    (js/React.DOM.div #js {:onClick (.. this -clicked)}
                      "modified render"
                      (.. this -props -message)
                      (-> (om/props this) :message))))
        msg "Hello, world!"
        shallow-renderer (.createRenderer test-utils)
        comp ((omfy/factory omified) {:message msg})
        _ (.render shallow-renderer comp)
        c (.getRenderOutput shallow-renderer)]
    (is (= (first (.. c -props -children)) "modified render"))
    (is (= (second (.. c -props -children)) msg))
    (is (= (nth (.. c -props -children) 2) msg)))
  (let [omified (omify OtherExample
                  Object
                  (componentWillReceiveProps [_ next-props]
                    (swap! update-atom assoc :componentWillReceiveProps next-props))
                  (render [this]
                    (js/React.DOM.div #js {:onClick (.. this -clicked)}
                      "modified render"
                      (.. this -props -message)
                      (-> (om/props this) :message))))
        msg "Hello, world!"
        shallow-renderer (.createRenderer test-utils)
        _ (.render shallow-renderer ((omfy/factory omified) {:message msg}))
        c (.getRenderOutput shallow-renderer)]
    (.render shallow-renderer ((omfy/factory omified) {:message msg}))
    (is (= (-> @update-atom :componentWillReceiveProps (gobj/get "message")) msg))
    #_(is (= (-> @update-atom :componentWillReceiveProps :message) msg)))
  (let [update-atom (atom {})
        omified (omify OtherExample
                  Object
                  (componentWillMount [this]
                    (swap! update-atom assoc :componentWillMount (.. this -props))))
        msg "Hello, world!"
        shallow-renderer (.createRenderer test-utils)
        _ (.render shallow-renderer ((omfy/factory omified) {:message msg}))
        c (.getRenderOutput shallow-renderer)]
    (is (= (-> @update-atom :componentWillMount (gobj/get "message")) msg)))
  (let [update-atom (atom {})
        omified (omify OtherExample
                  Object
                  (componentDidMount [this]
                    (swap! update-atom assoc :componentDidMount (.. this -props))))
        msg "Hello, world!"
        shallow-renderer (.createRenderer test-utils)
        _ (.render shallow-renderer ((omfy/factory omified) {:message msg}))
        c (.getRenderOutput shallow-renderer)]
    (.componentDidMount (.getMountedInstance shallow-renderer))
    (is (= (-> @update-atom :componentDidMount (gobj/get "message")) msg))
    #_(is (= (-> @update-atom :componentDidMount :message) msg)))
  (let [update-atom (atom {})
        omified (omify OtherExample
                  Object
                  (componentWillUpdate [this next-props next-state]
                    (swap! update-atom assoc
                      :componentWillUpdate {:props next-props
                                            :state next-state})))
        msg "Hello, world!"
        shallow-renderer (.createRenderer test-utils)
        _ (.render shallow-renderer ((omfy/factory omified) {:message msg}))
        c (.getRenderOutput shallow-renderer)]
    (.onClick (.. c -props))
    (let [updated @update-atom]
      (is (= (-> updated :componentWillUpdate :state (gobj/get "foo")) 43))
      (is (= (-> updated :componentWillUpdate :props (gobj/get "message")) msg))
      #_(is (= (-> updated :componentWillUpdate :props :message) msg))))
  (let [update-atom (atom {})
        omified (omify OtherExample
                  Object
                  (componentDidUpdate [this prev-props prev-state]
                    (swap! update-atom assoc
                      :componentDidUpdate {:props prev-props
                                           :state prev-state})))
        msg "Hello, world!"
        shallow-renderer (.createRenderer test-utils)
        _ (.render shallow-renderer ((omfy/factory omified) {:message msg}))
        c (.getRenderOutput shallow-renderer)]
    (.onClick (.. c -props))
    (let [updated @update-atom]
      (is (= (-> updated :componentDidUpdate :state (gobj/get "foo")) 42))
      (is (= (-> updated :componentDidUpdate :props (gobj/get "message")) msg))))
  (let [update-atom (atom {})
        omified (omify OtherExample
                  Object
                  (componentWillUnmount [this]
                    (swap! update-atom assoc :componentWillUnmount (.. this -props))))
        msg "Hello, world!"
        shallow-renderer (.createRenderer test-utils)
        _ (.render shallow-renderer ((omfy/factory omified) {:message msg}))
        c (.getRenderOutput shallow-renderer)]
    (.unmount shallow-renderer)
    (is (= (-> @update-atom :componentWillUnmount (gobj/get "message")) msg)))
  (let [update-atom (atom {})
        omified (omify OtherExample
                  Object
                  (shouldComponentUpdate [this next-props next-state]
                    (swap! update-atom assoc
                      :shouldComponentUpdate {:props next-props
                                              :state next-state})))
        msg "Hello, world!"
        shallow-renderer (.createRenderer test-utils)
        _ (.render shallow-renderer ((omfy/factory omified) {:message msg}))
        c (.getRenderOutput shallow-renderer)]
    (.onClick (.. c -props))
    (let [updated @update-atom]
      (is (= (-> updated :shouldComponentUpdate :state (gobj/get "foo")) 43))
      (is (= (-> updated :shouldComponentUpdate :props (gobj/get "message")) msg)))))
