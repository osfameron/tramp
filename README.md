# Tramp: threading macro with continuations

Tramp lets you write a thread like `->` but which stops when it
reaches a form prefixed by `!`

First define a function with `tramp->`

```clojure
(require '[tramp :refer [tramp->]])

(defn myfunc [i]
   (tramp-> i
            (inc)
            !(inc)
            (str)))
;; #'boot.user/myfunc
```

This function now returns a function...

```clojure
(myfunc 1)
;; #object[clojure.lang.AFunction$...]
```

... and that function returns the answer!

```clojure
((myfunc 1))
;; "3"
```

You could call the whole thing with:

```clojure
(trampoline myfunc 1)
;; "3"
```

Or you could stop to check that the intermediate steps are correct...

```clojure
(:fn (meta (myfunc 1)))
;; #object[clojure.core$inc ...]

(:args (meta (myfunc 1)))
;; [2]
```

You can also override the value your function should have returned:
(e.g. here we pretend that `(inc 2) => 10`)

```clojure
((myfunc 1) 10)
;; "10"
```

## Why?

This makes it possible to test functions that mix pure (core) logic and
effectful (shell) interactions without mocking.

Unlike mocks, the logic that is actually exercised is actually the code
that will be run in live.  The only thing that you need to override is
the values that the effectful functions would have returned.

For example:

```clojure
(defn get-parent-item [item]
   (tramp-> item
            :parent-id
            core/prepare-db-get-request
            !(effect/db-get-request)
            :item))
```

We could test it like:

```clojure
(deftest test-get-parent-item
   ; look Ma, no with-redefs!
   (let [step (get-parent-item {:id "ID" :parent-id "PARENT"})
         _ (is (= effect/db-get-request (:fn (meta step))))
         _ (is (= {:table "foo" :id "PARENT"} (:args (meta step))))
         result (step {:item {:id "PARENT"}})]
      (is (= {:id "PARENT"} result))))
```

## More features

Here is a fuller example of some features

```clojure
(tramp-> x
         inc          ; takes a function
         (inc)        ; or a form
         (+ 1)        ; thread first
         (+ 1 %)      ; or wherever you want it
         (guard odd?) ; short-circuit with nil unless condition holds
         (return 2)   ; short-circuit the thread with a value
         ! (effect % other args)) ; of course jump functions 
                                  ; also allow multiple paramters
