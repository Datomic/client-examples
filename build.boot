(deftask my-repl
  []
  (clojure.main/repl)
  (fn [next-task]
    (fn [fileset]
      (next-task fileset))))

(set-env!
 :dependencies
 '[[com.datomic/client-pro "0.8.14"]])
