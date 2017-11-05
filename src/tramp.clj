(ns tramp
  (:gen-class))

(defn jump 
  "Return a jump function to be called next.
  It takes function, arguments and a next-fn.

  When called with 0 args, it evaluates the function with the args
  and passes the result into the next-fn.

  When called with 1 argument, this is an 'override', which is
  passed directly to the next-fn.

  This function is introspectable with meta keys {:fn, :args, :next-fn}"
  [f args next-fn]
  (with-meta 
    (fn [& [override :as overridden?]]
        (next-fn (if (seq overridden?)
                   override
                   (apply f args))))
    {:fn f :args args :next-fn next-fn})) 

(defmacro jump' [arg f args next-fn]
  `(jump ~f [~@(cons arg args)] ~next-fn))

(defn split-exclusive 
  "Like split-with, but removes the item matching the predicate"
  [p coll]
  (let [[a [_ & b]] (split-with p coll)]
    [a b]))

(defn tramp->* 
  "function backend to tramp-> macro"
  [v forms]
  (let [[a b] (split-exclusive #(not= '! %) forms)
        pure `(-> ~v
                  ~@a)
        jumped (when (seq b)
                 (let [[[f & args] & bs] b
                       v (gensym)]
                   `((jump' ~f
                           ~args
                           (fn [~v] ~(tramp->* v bs))))))]
    (concat pure jumped)))

(defmacro tramp-> 
  "Threading macro like `->`
  Takes a value v and a number of forms.

  The forms may be prefixed by `!` in which case instead of proceeding
  they return a `jump` function which will continue the thread when called.

  You can call a tramp-> thread using `trampoline`:
    (trampoline (tramp-> 1 !(inc) !(inc) !(inc))) => 3"
  [v & forms]
  (tramp->* v forms))
