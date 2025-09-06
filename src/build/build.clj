(ns build
  (:require [clojure.tools.build.api :as b]))


(defn make-pom [{:keys [version]}]
  (b/write-pom {:basis   (b/create-basis)
                :target  "."
                :lib     'jarppe.clj-helidon/clj-helidon
                :version version}))
