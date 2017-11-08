# Tramp: threading macro with continuations

Tramp has a single macro `tramp->` which lets you write a thread like `->`, but
also `->>` or `some->` if you need it, as well as resumable continuations.

```clojure
(require '[tramp :refer [tramp->]])

(tramp-> 1 inc inc)
;; => 3
```

## Customizable ordering

If you don't want thread-first, you can specify the position of the
threaded argument with `%`:

```clojure
(tramp-> 3
         range
         (map inc %))
;; => (1 2 3)
```

## Guards!

You might use `some->` to create a thread which terminates on seeing any
`nil` value.  But sometimes it's hard to tell which forms in the thread
you're expecting to return a nil.  And sometimes you'd like to use a
*different* measure of success.

```clojure
(tramp-> id
         lookup-in-db
         (guard some?)
         parent-id
         (guard some?))

(tramp-> num
         inc
         (guard odd?)
         (str "The answer was " %)))
```

You can also `return` a thread early with:

```clojure
(tramp-> num
         inc
         (return 5)
         inc ;; <-- rest of this thread will never be called
         inc)
```

## If

The `if->` macro takes a predicate, a true branch and an optional
false branch.  These branches are themselves `tramp->` threads.

If no false branch is provided, the value is passed through unchanged.

```clojure
(tramp-> i
         (if-> odd?
            ((inc)
             (* 2))
            ((* 3)
             (dec)))
         (str "The answer is: " %))

; always return an even number
(tramp-> i
         (if-> odd?
            (inc)))
```

## Break on `!`

```clojure
(defn myfunc [i]
   (tramp-> i
            (inc)
            ! (inc)
            (str)))
;; #'boot.user/myfunc
```

Because this function call has an ! in it before the second `(inc)`
form, it will break on that when called, returning a function:

```clojure
(myfunc 1)
;; #object[clojure.lang.AFunction$...]
```

... and that function returns the answer!

```clojure
((myfunc 1))
;; "3"
```

Obviously calling functions like `((((f))))` all the time would be
horrendous!  Luckily Clojure has a builtin function that recursively calls a
function until the final result is returned: `trampoline`.

So you could call the whole thing with:

```clojure
(trampoline myfunc 1)
;; "3"
```

But the intermediate functions are annotated with metadata.  So
you could also stop to check that the intermediate steps are correct...

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

; or, if you prefer using the helper `step!`:

```clojure
(-> (myfunc 1) (step! 10))
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

We can't easily test that `(effect/db-get-request)` will return
the right value, but we know what it *should* return given that
input.

So we could test it like this (using some additional helper macros
which call `clojure.test/is`:

```clojure
(deftest test-get-parent-item
   (testing "look Ma, no with-redefs!"
      (-> (get-parent-item {:id "ID" :parent-id "PARENT"})
          (is-fn effect/db-get-request)
          (is-arg {:table "foo" :id "PARENT"})
          (step! {:item {:id "PARENT"}})]
          (is-result {:id "PARENT"})))
```
