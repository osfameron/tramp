(def project 'tramp)
(def version "0.1.0-SNAPSHOT")

(set-env! :source-paths   #{"src" "test"}
          :dependencies   '[[org.clojure/clojure "1.8.0"]
                            [adzerk/boot-test "RELEASE" :scope "test"]])

(task-options!
 pom {:project     project
      :version     version
      :description "Tramp: threading macro which returns continuations"
      :url         "https://github.com/osfameron/"
      :scm         {:url "https://github.com/osfameron/"}
      :license     {"Eclipse Public License"
                    "http://www.eclipse.org/legal/epl-v10.html"}}
 jar {:main        'tramp
      :file        (str "tramp-" version "-standalone.jar")})

(deftask build
  "Build the project locally as a JAR."
  [d dir PATH #{str} "the set of directories to write to (target)."]
  (let [dir (if (seq dir) dir #{"target"})]
    (comp (pom) (uber) (jar) (target :dir dir))))

(deftask run
  "Run the project."
  [a args ARG [str] "the arguments for the application."]
  (require '[scratch.scratch :as app])
  (apply (resolve 'app/-main) args))

(require '[adzerk.boot-test :refer [test]])
