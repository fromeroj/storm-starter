(ns storm.starter.clj .word-count
  (:import [backtype.storm StormSubmitter LocalCluster])
  (:use [backtype.storm clojure config])
  (:gen-class))

(defspout sentence-spout ["sentence"]
  [conf context collector]
  (let
      [sentences ["Ten presente que se me cuenta"
                  "entre los teoricos estudiosos"
                  "y principales practicantes"
                  "de la ley del minimo esfuerzo"]]
    (spout
     (nextTuple []
       (Thread/sleep 1000)
       (emit-spout! collector [(rand-nth sentences)])         
       )
     (ack [id]
        ;; Esto es para spouts confiable, que necesitan "dar recibo"
        ))))

(defspout sentence-spout-parameterized ["word"] 
  {:params [sentences] :prepare false}
  [collector]
  (Thread/sleep 500)
  (emit-spout! collector [(rand-nth sentences)]))

(defbolt split-sentence ["word"] 
  [tuple collector]
  (let [words (.split (.getString tuple 0) " ")]
    (doseq [w words]
      (emit-bolt! collector [w] :anchor tuple))
    (ack! collector tuple)
    ))

(defbolt word-count ["word" "count"] 
  {:prepare true}
  [conf context collector]
  (let [counts (atom {})]
    (bolt
     (execute [tuple]
       (let [word (.getString tuple 0)]
         (swap! counts (partial merge-with +) {word 1})
         (emit-bolt! collector [word (@counts word)] :anchor tuple)
         (ack! collector tuple)
         )))))

(defn mk-topology []
  (topology
   {
    "1" (spout-spec sentence-spout)
    "2" (spout-spec (sentence-spout-parameterized
                     ["oye bartola ahi te dejo estos dos pesos"
                      "pagas la renta el telefono y la luz"])
                     :p 2)
    }
   {
    "3" (bolt-spec {"1" :shuffle "2" :shuffle}
                   split-sentence
                   :p 5)
    "4" (bolt-spec {"3" ["word"]}
                   word-count
                   :p 6)
    }
   ))


(defn run-local! []
  (let [cluster (LocalCluster.)]
    (.submitTopology cluster "word-count" {TOPOLOGY-DEBUG true} (mk-topology) )
    (Thread/sleep 10000)
    (.shutdown cluster)
    ))

(defn submit-topology! [name]
  (StormSubmitter/submitTopology
   name
   {TOPOLOGY-DEBUG true
    TOPOLOGY-WORKERS 3}
   (mk-topology)))

(defn -main
  ([]
   (run-local!))
  ([name]
   (submit-topology! name)))

;lein-compile
;lein run -m storm.starter.clj.word-count