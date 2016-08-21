(def +version+ "0.1.0")

(set-env!
 :source-paths    #{"src/main"}
 :resource-paths  #{"resources"}
 :dependencies '[[org.clojure/clojurescript   "1.9.225"        :scope "provided"]
                 [org.omcljs/om               "1.0.0-alpha41"  :scope "provided"
                  :exclusions [cljsjs/react]]
                 [cljsjs/react-with-addons    "15.3.0-0"       :scope "test"]
                 [cljsjs/react-dom            "15.3.0-0"       :scope "test"
                  :exclusions [cljsjs/react]]
                 [com.cognitect/transit-clj   "0.8.288"        :scope "test"]
                 [devcards                    "0.2.1-7"        :scope "test"
                  :exclusions [cljsjs/react cljsjs/react-dom]]
                 [com.cemerick/piggieback     "0.2.1"          :scope "test"]
                 [pandeiro/boot-http          "0.7.3"          :scope "test"]
                 [adzerk/boot-cljs            "1.7.228-1"      :scope "test"]
                 [adzerk/boot-cljs-repl       "0.3.3"          :scope "test"]
                 [crisptrutski/boot-cljs-test "0.2.2-SNAPSHOT" :scope "test"]
                 [adzerk/boot-reload          "0.4.12"         :scope "test"]
                 [adzerk/bootlaces            "0.1.13"         :scope "test"]
                 [org.clojure/tools.nrepl     "0.2.12"         :scope "test"]
                 [weasel                      "0.7.0"          :scope "test"]
                 [boot-codox                  "0.9.6"          :scope "test"]])

(require
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
 '[adzerk.boot-reload    :refer [reload]]
 '[adzerk.bootlaces      :refer [bootlaces! push-release]]
 '[crisptrutski.boot-cljs-test :refer [test-cljs]]
 '[pandeiro.boot-http :refer [serve]]
 '[codox.boot :refer [codox]])

(bootlaces! +version+ :dont-modify-paths? true)

(task-options!
  pom {:project 'omify
       :version +version+
       :description "Make plain React components Om Next compatible"
       :url "https://github.com/compassus/omify"
       :scm {:url "https://github.com/compassus/omify"}
       :license {"name" "Eclipse Public License"
                 "url"  "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask build-jar []
  (set-env! :resource-paths #{"src/main"})
  (adzerk.bootlaces/build-jar))

(deftask release-clojars! []
  (comp
    (build-jar)
    (push-release)))

(deftask deps [])

(deftask devcards []
  (set-env! :source-paths #(conj % "src/devcards"))
  (comp
    (serve)
    (watch)
    (cljs-repl)
    (reload :on-jsload 'omify.devcards.core/init!)
    (speak)
    (cljs :source-map true
          ;:optimizations :advanced
          :compiler-options {:devcards true
                             :foreign-libs [{:file "resources/js/Recharts.min.js"
                                             :requires ["cljsjs.react" "cljsjs.react.dom"]
                                             :provides ["cljsjs.recharts"]}]
                             :parallel-build true
                             :externs ["resources/js/Recharts.ext.js"]}
          :ids #{"js/devcards"})))

(deftask testing []
  (set-env! :source-paths #(conj % "src/test"))
  identity)

(ns-unmap 'boot.user 'test)

(deftask test
  [e exit?     bool  "Enable flag."]
  (let [exit? (cond-> exit?
                (nil? exit?) not)]
    (comp
      (testing)
      (test-cljs
        :js-env :node
        :namespaces #{'omify.tests}
        :cljs-opts {:parallel-build true}
        :exit? exit?))))

(deftask auto-test []
  (comp
    (watch)
    (speak)
    (test :exit? false)))

(deftask release-devcards []
  (set-env! :source-paths #(conj % "src/devcards"))
  (comp
    (cljs :optimizations :advanced
      :ids #{"js/devcards"}
      :compiler-options {:devcards true
                         :elide-asserts true})
    (target)))
