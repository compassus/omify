(ns omify.core
  (:require [om.next :as om]
            [cljs.core :refer [specify!]]
            [cljs.analyzer :as ana]))

(def lifecycle-methods
  '[initLocalState componentWillMount componentDidMount
    componentWillReceiveProps shouldComponentUpdate componentWillUpdate
    componentDidUpdate render componentWillUnmount])

(defn make-reshape-map [old-meths]
  (let [this (gensym "this_")]
    {:reshape
     {'componentWillReceiveProps
      (fn [[name [self next-props :as args] & body]]
        `(~name [this# next-props#]
          (let [~self this#
                ~this this#
                ~next-props (cljs.core/clj->js next-props#)]
            ~@body)))}
     :defaults
     `{~'componentWillMount
       ([~this]
        (when-let [f# (goog.object/get ~old-meths "componentWillMount")]
          (.call f# ~this)))
       ~'componentDidMount
       ([~this]
        (when-let [f# (goog.object/get ~old-meths "componentDidMount")]
          (.call f# ~this)))
       ~'componentWillReceiveProps
       ([~this next-props#]
        (when-let [f# (goog.object/get ~old-meths "componentWillReceiveProps")]
          (.call f# ~this
            (cljs.core/clj->js (merge next-props#
                                 (om.next/get-computed next-props#))))))
       ~'shouldComponentUpdate
       ([~this next-props# next-state#]
        (if-let [f# (goog.object/get ~old-meths "shouldComponentUpdate")]
          (let [next-props# (goog.object/clone next-props#)]
            (doseq [prop# ["omcljs$reconciler" "omcljs$reactKey" "omcljs$value"
                           "omcljs$path" "omcljs$parent" "omcljs$shared"
                           "omcljs$instrument" "omcljs$depth"]]
              (goog.object/remove next-props# prop#))
            (.call f# ~this next-props# next-state#))
          true))
       ~'componentWillUpdate
       ([~this next-props# next-state#]
        (let [next-props# (om.next/-next-props next-props# ~this)
              ret# (when-let [f# (goog.object/get ~old-meths "componentWillUpdate")]
                     (.call f# ~this
                       (cljs.core/clj->js (merge next-props# (om.next/get-computed next-props#)))
                       next-state#))]
          (when (cljs.core/implements? om.next/Ident ~this)
            (let [ident# (om.next/ident ~this (om.next/props ~this))
                  next-ident# (om.next/ident ~this next-props#)]
              (when (not= ident# next-ident#)
                (let [idxr# (get-in (om.next/get-reconciler ~this) [:config :indexer])]
                  (when-not (nil? idxr#)
                    (swap! (:indexes idxr#)
                      (fn [indexes#]
                        (-> indexes#
                          (update-in [:ref->components ident#] disj ~this)
                          (update-in [:ref->components next-ident#] (fnil conj #{}) ~this)))))))))
          (om.next/merge-pending-props! ~this)
          (om.next/merge-pending-state! ~this)
          ret#))
       ~'componentDidUpdate
       ([~this prev-props# prev-state#]
        (when-let [f# (goog.object/get ~old-meths "componentDidUpdate")]
          (.call f# ~this prev-props# prev-state#))
        (om.next/clear-prev-props! ~this))
       ~'componentWillUnmount
       ([~this]
        (when-let [f# (goog.object/get ~old-meths "componentWillUnmount")]
          (.call f# ~this)))
       ~'render
       ([~this]
        (when-let [f# (goog.object/get ~old-meths "render")]
          (.call f# ~this)))}}))

(defn reshape [dt {:keys [reshape defaults]}]
  (letfn [(reshape* [x]
            (if (and (sequential? x)
                     (contains? reshape (first x)))
              (let [reshapef (get reshape (first x))]
                ;; fails for `shouldComponentUpdate`
                ;; without https://github.com/omcljs/om/pull/751
                ;(om.next/validate-sig x)
                (reshapef x))
              x))
          (add-defaults-step [ret meth]
            (let [impl (get defaults meth)]
              (if-not (or (nil? impl) (some #{meth} (map first (filter seq? dt))))
                (let [[before [p & after]] (split-with (complement '#{Object}) ret)]
                  (into (conj (vec before) p (cons meth impl)) after))
                ret)))
          (add-defaults [dt]
            (reduce add-defaults-step dt lifecycle-methods))
          (add-object-protocol [dt]
            (if-not (some '#{Object} dt)
              (conj dt 'Object)
              dt))]
    (->> dt (map reshape*) vec add-object-protocol add-defaults)))

(defn omify* [name forms mutate? env]
  (let [{:keys [dt statics]} (om/collect-statics forms)
        x (gensym "x")
        old-meths (gensym "old-meths")
        rname (if env
                (:name (ana/resolve-var (dissoc env :locals) name))
                name)]
    `(let [~x (if ~mutate?
                (let [original# ~name]
                  (defn ~(with-meta name {:jsdoc ["@constructor"]}) []
                    (cljs.core/this-as this#
                      (.call original# this# (cljs.core/js-arguments))
                      (if-not (nil? (.-initLocalState this#))
                        (let [st# (.initLocalState this#)]
                          (set! (.-state this#) (js/Object.assign (.initLocalState this#)
                                                  (goog.object/get st# "omcljs$state"))))
                        (when (nil? (.-state this#))
                          (set! (.-state this#) (cljs.core/js-obj))))
                      this#))
                  (set! (.. ~name -prototype) (.. original# -prototype))
                  ~name)
                (let [clone# (fn ~(with-meta 'Constructor {:jsdoc ["@constructor"]}) []
                               (cljs.core/this-as this#
                                 (.call ~name this# (cljs.core/js-arguments))
                                 (if-not (nil? (.-initLocalState this#))
                                   (let [st# (.initLocalState this#)]
                                     (set! (.-state this#) (js/Object.assign (.initLocalState this#)
                                                             (goog.object/get st# "omcljs$state"))))
                                   (when (nil? (.-state this#))
                                     (set! (.-state this#) (cljs.core/js-obj))))
                                 this#))]
                  (set! (.. clone# -prototype) (js/Object.create (.. ~name -prototype)))
                  (set! (.. clone# -defaultProps) (.. ~name -defaultProps))
                  (set! (.. clone# -prototype -constructor) clone#)
                  (set! (.. clone# -prototype -constructor -displayName)
                    (.. ~name -prototype -constructor -displayName))
                  clone#))]
       (set! (.. ~x ~'-prototype -om$isComponent) true)
       (let [~old-meths (cljs.core/js-obj)]
         ~@(map (fn [meth]
                  `(when-let [meth# (.. ~x ~'-prototype ~(symbol (str "-" meth)))]
                     (goog.object/set ~old-meths (str '~meth) meth#)))
             lifecycle-methods)
         (specify! (.. ~x ~'-prototype)
           ~@(let [dt' (reshape dt (make-reshape-map old-meths))]
               (om/reshape dt' (update-in om/reshape-map [:reshape]
                                 dissoc 'componentWillUpdate 'componentDidUpdate)))))
       (specify! ~x
         ~@(mapv #(cond-> %
                    (symbol? %) (vary-meta assoc :static true)) (:protocols statics)))
       (specify! (. ~x ~'-prototype) ~@(:protocols statics))
       (set! (.-cljs$lang$type ~x) true)
       (set! (.-cljs$lang$ctorStr ~x) ~(str rname))
       (set! (.-cljs$lang$ctorPrWriter ~x)
         (fn [this# writer# opt#]
           (cljs.core/-write writer# ~(str rname))))
       ~x)))


(defmacro omify [name & forms]
  (omify* name forms false &env))

(defmacro omify! [name & forms]
  (omify* name forms true &env))


(comment
  #_(fn ~(with-meta 'Constructor {:jsdoc ["@constructor"]}) [props# ctx# updater#]
                        (cljs.core/this-as this#
                          (js/console.log "LOL IM HERE" props#)
                          (let [props2# (or (goog.object/get props# "omcljs$value") props#)
                                props2# (cond-> props2#
                                          (instance? om.next/OmProps props2#) om.next/unwrap)
                                computed# (js/Object.assign (cljs.core/js-obj)
                                            props#
                                            (cljs.core/clj->js props2#))]
                            ;; TODO: process children

                            ;; React auto-binds methods
                            ;; https://github.com/facebook/react/blob/869cb/src/isomorphic/classic/class/ReactClass.js#L700
                            #_(let [abp# (.. ~name -prototype -__reactAutoBindPairs)
                                  len# (count abp#)]
                              (loop [i# 0]
                                (when (< i# len#)
                                  (let [k# (aget abp# i#)
                                        f# (aget abp# (inc i#))]
                                    (gobj/set this# k# (.bind f# this#))
                                    (recur (+ i# 2))))))
                            (.apply constructor# this#
                              (cljs.core/array computed# ctx# updater#))
                            #_(if (cljs.core/exists? (.-getInitialState this#))
                              (set! (.-state this#) (.getInitialState this#))
                              (set! (.-state this#) (cljs.core/js-obj)))
                            this#)))
;; mutate? true

  (let [proto# (.. ~name -prototype)
                      constructor# (.. ~name -prototype -constructor)
                      display-name# (.. ~name -prototype -constructor -displayName)
                      ;; TODO: there might be some other stuff just like defaultProps
                      ;; that we need to set on the new constructor
                      ;; https://github.com/facebook/react/blob/869cb/src/isomorphic/classic/element/ReactElement.js
                      default-props# (.. ~name -defaultProps)
                      ext#
                      (fn ~(with-meta name {:jsdoc ["@constructor"]})
                        [props# ctx# updater#]
                        (cljs.core/this-as this#
                          (let [props2# (or (goog.object/get props# "omcljs$value") props#)
                                props2# (cond-> props2#
                                          (instance? om.next/OmProps props2#) om.next/unwrap)
                                computed# (js/Object.assign (cljs.core/js-obj)
                                            props#
                                            (cljs.core/clj->js props2#))]
                            (when-let [children# (.. computed# -children)]
                              (let [single?# (not (sequential? children#))
                                    ret# (cljs.core/array)]
                                (doseq [child# (if single?# [children#] children#)]
                                  (if (js/React.isValidElement child#)
                                    (if-let [props# (goog.object/get (.. child# -props) "omcljs$value")]
                                      (do
                                        #_(.push ret#
                                          (.apply js/React.createElement
                                            (.. child# -type)
                                            (js/Object.assign (cljs.core/js-obj)
                                              (.. child# -props)
                                              (cljs.core/clj->js (om.next/unwrap props#)))
                                            (.. child# -props -children)))
                                        (set! (.. child# -props)
                                          (js/Object.assign (cljs.core/js-obj)
                                            (.. child# -props)
                                            (cljs.core/clj->js (om.next/unwrap props#))))
                                        (.push ret# child#)
                                          #_(.log js/console "children crlh" ret#))
                                      (.push ret# child#))
                                    (.push ret# child#)))
                                (set! (.. computed# -children) (if single?# (first ret#) ret#))))
                            #_(.log js/console "overriding!" computed#)

                            (.apply constructor# this#
                              (cljs.core/array
                                computed#
                                ctx# updater#)))
                          this#))]
                  (set! ~name ext#)
                  (set! (.. ~name -prototype) proto#)
                  (set! (.. ~name -defaultProps) default-props#)
                  (set! (.. ~name -prototype -constructor) ext#)
                  (set! (.. ~name -prototype -constructor -displayName) display-name#)
                  ~name)

  )
