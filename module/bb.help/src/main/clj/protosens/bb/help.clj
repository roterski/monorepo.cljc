(ns protosens.bb.help

  "Print extra documentation for Babashka tasks."

  (:refer-clojure :exclude [print])
  (:require [clojure.edn             :as edn]
            [clojure.string          :as string]
            [protosens.bb.help.print :as $.bb.help.print]
            [protosens.string        :as $.string]))


(declare printer+)


;;;;;;;;;; Private


(defn- -task+

  ;; Retrieves all task from a `bb.edn` file.

  [option+]

  (-> (or (:bb option+)
          "bb.edn")
      (slurp)
      (edn/read-string)
      (:tasks)
      (not-empty)))
  

;;;;;;;;;; Print documentation


(defn task

  "Pretty-prints extra documentation for a task (if there is any).

   Extra documentation may be specified in a task under `:module/maestro`.
   Multi-line strings will be realigned.

   Options may contain:

   | Key     | Value                                          | Default       |
   |---------|------------------------------------------------|---------------|
   | `:bb`   | Path to the Babashka config file hosting tasks | `\"bb.edn\"`  |
   | `:task` | Task to print (without extension)              | First CLI arg |
  
   Returns a data map that can be printed with [[print]]."

  
  ([]

   (task nil))


  ([option+]

   (if-some [task+ (-task+ option+)]
     (let [result {:task+ task+}]
       (if-some [task (or (:task option+)
                          (some-> (first *command-line-args*)
                                  (symbol)))]
         (let [result-2 (assoc result
                               :task
                               task)]
           (if-some [data (get task+
                               task)]
             (assoc result-2
                    :body      (some-> (data :maestro/doc)
                                       ($.string/realign))
                    :docstring (data :doc)
                    :type      :task)
             (assoc result-2
                    :type
                    :not-found)))
         (assoc result
                :type
                :no-task)))
     {:type :no-task+})))


(defn undocumented-task+

  "Returns a sorted list of tasks which do not have a `:maestro/doc`.

   Options may be:

   | Key   | Value                                          | Default      |
   |-------|------------------------------------------------|--------------|
   | `:bb` | Path to the Babashka config file hosting tasks | `\"bb.edn\"` |
  
   The return value is a data map that can be printed with [[print]]."


  ([]

   (undocumented-task+ nil))


  ([option+]

   {:task+ (sort-by string/lower-case
                    (keep (fn [[task data]]
                            (when-not (:maestro/doc data)
                              task))
                          (-task+ option+)))
    :type  :undocumented-task+}))


;;;;;;;;;; Entry point to printing


(defn print

  "Pretty-prints data maps returned from other functions.

   See [[task]], [[undocumented-task+]].
  
   Those data maps have a `:type` this function uses for dispatching them to a
   printer function located under `:printer+`.
   Uses [[printer+]] by default but any custom one can be provided under `:printer+`
   to be merged with those."

  [data]

  (let [data-2  (update data
                        :printer+
                        #(merge printer+
                                %))
        printer (get-in data-2
                        [:printer+
                         (data-2 :type)])]
    (printer data-2)))



(def printer+

  "Default printers used by [[print]].

   They come from the [[protosens.bb.help.print]] namespace."
  
  {:no-task            $.bb.help.print/no-task
   :no-task+           $.bb.help.print/no-task+
   :not-found          $.bb.help.print/not-found
   :task               $.bb.help.print/task
   :undocumented-task+ $.bb.help.print/undocumented-task+})
