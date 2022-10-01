(ns protosens.maestro.uber

  "Special way of merging aliases in a generated `deps.edn` file."

  (:require [babashka.fs               :as bb.fs]
            [clojure.java.io           :as java.io]
            [clojure.pprint            :as pprint]
            [protosens.maestro         :as $.maestro]
            [protosens.maestro.alias   :as $.maestro.alias]
            [protosens.maestro.profile :as $.maestro.profile]))


;;;;;;;;;; Implementation


(defn- -create-link+

  ;; Creates hard links for all files found in merged paths, besides
  ;; those that are descendant of the root.

  [deps-edn root root-uber-abs]

  (doseq [path (sort (deps-edn :paths))]
    (doseq [path-child (map str
                            (file-seq (java.io/file path)))
            :when      (not (or (bb.fs/directory? path-child)
                                (bb.fs/starts-with? path-child
                                                    root)))
            :let       [path-link (str root-uber-abs
                                       "/"
                                       path-child)
                        dir       (bb.fs/parent path-link)]]
      (bb.fs/create-dirs dir)
      (println "HARD LINK" path-link "->" path-child)
      (bb.fs/create-link path-link
                         path-child))))



(defn- -delete-old-link+

  ;; Deletes previously generated hard links.

  [root-uber-abs]

  (when (bb.fs/exists? root-uber-abs)
    (println "DELETE previously generated hard links:"
             root-uber-abs)
    (bb.fs/delete-tree root-uber-abs)))



(defn- -write-deps-edn

  ;; Pretty-prints the generate `deps.edn` file.

  [basis root root-uber-rel deps-edn]

  (with-open [out-deps-edn (java.io/writer (str root
                                                "/deps.edn"))]
    (binding [*out* out-deps-edn]
      (println ";; This file has been generated by Maestro.")
      (println ";;")
      (println ";; It merges dependencies and paths from a collection of aliases:")
      (println ";;")
      (doseq [alias (sort (basis :maestro/require))]
        (println ";;    "
                 alias))
      (println ";;")
      (println ";; All files found in those paths are hard links to follow from the")
      (println ";; root of this repository.")
      (println ";;")
      (println ";; This is probably for dev purposes only and should probably never")
      (println ";; be checkout out in version control.")
      (println ";;")
      (pprint/pprint (update deps-edn
                             :paths
                             (fn [path+]
                               (-> (map (fn [path]
                                          (if (bb.fs/starts-with? path
                                                                  root)
                                            (str (bb.fs/relativize root
                                                                   path))
                                            (str root-uber-rel
                                                 "/"
                                                 path)))
                                        path+)
                                   (sort)
                                   (vec))))))))


;;;;;;;;;; Public


(defn task

  "Generate a single `deps.edn` file by merging everything required by `alias`.

   This is probably only useful in a limited set of dev use cases. One notable
   example is syncing dependencies and paths with [Babashka](https://github.com/babashka/babashka)'s
   `bb.edn` files. One can create:

   - Create an alias in `deps.edn` with a `:maestro/root`, requiring other aliases
   - Run this task on this alias
   - Generated `deps.edn` file in `:maestro/root` will contain all necessary `:deps` and `:paths`
   - Hard links were created for all files found in `:paths`
   - `bb.edn` can use `:local/root` on this

   Hard links are created to allow consuming paths from anywhere in the repository.
   This is because Clojure CLI dislikes outsider paths (e.g. `../foo`). They are generated in
   `./maestro/uber` relative to the `:maestro/root`."


  ([alias]

   (task alias 
         nil))


  ([alias basis]

   (let [basis-2     (-> basis
                         ($.maestro/ensure-basis)
                         ($.maestro.alias/append+ [alias])
                         ($.maestro.profile/append+ ['release]))
         alias->data (basis-2 :aliases)
         data        (alias->data alias)
         _           (when-not data
                       ($.maestro/fail (str "No data found for alias: "
                                            alias)))
         basis-3     ($.maestro/search basis-2)
         root        (data :maestro/root)
         _           (when-not root
                       ($.maestro/fail (str "Given alias does not contain `:maestro/root`")))
         ;;
         ;;          Merges dependencies and paths.
         deps-edn    (reduce (fn [deps-edn alias-required]
                               (let [data-required (alias->data alias-required)]
                                 (-> deps-edn
                                     (update :deps
                                             merge
                                             (:extra-deps data-required))
                                     (update :paths
                                             into
                                             (:extra-paths data-required)))))
                             {:deps  {}
                              :paths #{}}
                             (basis-3 :maestro/require))
         ;;
         root-uber-rel "maestro/uber"
         root-uber-abs (str root
                            "/"
                            root-uber-rel)]
     (-write-deps-edn basis-3
                      root
                      root-uber-rel
                      deps-edn)
     (-delete-old-link+ root-uber-abs)
     (-create-link+ deps-edn
                    root
                    root-uber-abs))))
