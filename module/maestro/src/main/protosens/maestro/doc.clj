(ns protosens.maestro.doc

  "Collection of miscellaneous helpers related to documentation."

  (:require [babashka.fs :as bb.fs]
            [clojure.edn :as edn]))


;;;;;;;;;;


(defn task

  "Prints documentation for a Babashka task.
  
   `root` is a path to a directory hosting documentation text files, one per task.
   Those must be named after the task they document.

   For instance, at the root of public Protosens monorepo, try:

   ```
   bb doc deploy:clojars
   ```

   Options may be:

   | Key          | Value                                          | Default      |
   |--------------|------------------------------------------------|--------------|
   | `:bb`        | Path to the Babashka config file hosting tasks | `\"bb.edn\"` |
   | `:extension` | Extension of text files in the root directory  | `\".txt\"`   |"


  ([root]

   (task root
         nil))


  ([root option+]

   (when-some [task (or (:task option+)
                        (some-> (first *command-line-args*)
                                (symbol)))]
     (if-some [task-data (-> (slurp (or (:bb option+)
                                    "bb.edn"))
                         (edn/read-string)
                         (get-in [:tasks
                                  task]))]
       (let [docstring (:doc task-data)
             path-body (str root
                            "/"
                            task
                            (or (:extension option+)
                                ".txt"))
             body      (if (bb.fs/exists? path-body)
                         (slurp path-body)
                         "No documentation found for this task.")]
         (when docstring
           (println docstring)
           (println)
           (println "---")
           (println))
         (println body))
       (println "Task not found.")))))
