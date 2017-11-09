(ns tramp
  (:require [clojure.test :as test])
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

(defn split-on 
  "Splits around the item matching the predicate, exclusive."
  [p coll]
  (let [[a [_ & b]] (split-with (complement p) coll)]
    [a b]))

(def %? #{'%})
(def !? #{'!})

(defn seq->template
  "Given a sequence of (f & args) inserts a % in the first position if
  none is already present.
    e.g.
      (seq->template '(f 1))   => '(f % 1)
      (seq->template '(f % 1)) => '(f % 1)
      (seq->template '(f 1 %)) => '(f 1 %)"
  [[f & args]]
  (if (seq? args)
    (if (some %? args)
      (cons f args)
      (concat [f '%] args))
    [f]))

(defn form->template
  "Given a form, returns a template of function and args, with the %
  in the location to be threaded.  The case of [f %] is returned as [f]"
  [form]
  (if (seq? form)
    (seq->template form)
    [form]))

(defn template->function [[f & args]]
  "Given a template, return a function which will accept
  an arg in the correct place (delimited by %)"
  (if (seq args)
    (let [[a b] (split-on %? args)] 
      `(fn [~'arg]
         (~f ~@a ~'arg ~@b)))
    f))

(defn form->function [form]
  (-> form
      form->template
      template->function))

(defn -right [v f] (list f v))
(defn -step [v f] (f v))

(comment
  (defn tramp! [f & args]
    (if (fn? f)
      (reduce apply f (cons args (repeat [])))
      f)))

(defn tramp->* 
  "function backend to tramp-> macro"
  [v forms]
  (let [[a b] (split-on !? forms)
        pure (map form->function a)
        jumped (when (seq b)
                 (let [[b & bs] b
                       [f & args] (form->template b)
                       [a b] (split-on %? args)] 
                   `((fn [arg#]
                       (let [args# [~@a arg# ~@b]
                             next-fn# (fn [~'next-arg]
                                        ~(tramp->* 'next-arg bs))]
                         (jump ~f args# next-fn#))))))] 
    `(reduce -step ~v [~@pure ~@jumped])))

(defmacro tramp-> 
  "Threading macro like `->`
  Takes a value v and a number of forms.

  The forms may be prefixed by `!` in which case instead of proceeding
  they return a `jump` function which will continue the thread when called.

  You can call a tramp-> thread using `trampoline`:
    (trampoline (tramp-> 1 !(inc) !(inc) !(inc))) => 3"
  [v & forms]
  `(do
     (defonce ~'tramp-nesting 0)
     (let [~'tramp-nesting (inc ~'tramp-nesting)]
       ~(tramp->* v forms))))

(defn return* [nesting v]
  ; can't use -step because a reduced value short-circuits
  ; the reduce
  (eval 
    (reduce -right
            v
            (repeat nesting reduced))))

(defmacro return
  "Return a value immediately, exiting the thread."
  [_ v]
  `(return* ~'tramp-nesting ~v))

(defn guard
  "Function for use in a tramp-> thread.
  Takes a predicate, and asserts that it is true.
  Otherwise returns nil or the supplied `ret` value.
  
  Unlike `return`, will only return out of the current
  level of thread."
  [v pred & [ret]]
  (if (pred v)
    v
    (reduced ret)))

(defn step! 
  "Calls the jump function.
  Passes any arguments onwards.
  The jump function can take a single optional argument, which acts as an override:
    e.g. that value will be passed on instead of calling the jump function."
  [a & args]
  (apply a args))

(defmacro is-result [a expected]
  "Macro for use inside a test thread.  
  Tests with `is =` that the thread has completed
  and the result is correct."
  `(test/is (= ~expected ~a)))

(defmacro is-fn
  "Macro for use inside a test thread.  
  Tests with `is =` that the function to be
  passed to the jump function is as expected"
  [a f]
  `(do
     (test/is (= ~f (:fn (meta ~a))))
     ~a))

(defmacro is-args 
  "Macro for use inside a test thread.  
  Tests with `is =` that the args to be
  passed to the jump function are as
  expected.  These will include the current
  value of the thread, threaded into the
  appropriate position."
  [a args]
  `(do
     (test/is (= [~@args] (:args (meta ~a))))
     ~a))

(defmacro is-value
  "Macro for use inside a test thread.  
  Tests with `is =` that the specified
  value is the current value in the thread
  before calling the jump function."
  [a v]
  `(do
     (test/is (= ~v (first (:args (meta ~a)))))
     ~a))

(defmacro if->
  "Macro for use inside tramp-> thread.
   Takes:
    the threaded value `a`
    predicate `p`
    true branch `t`
    optional false branch `f`.
   If the predicate holds, then a new `tramp->`
   branch is run on the true branch.

   Else the false branch is run if present,
   otherwise the threaded value is returned unchanged."
  [a p t & [f]]
  `(#(if (~p ~a)
       (tramp-> ~a ~@t)
       (tramp-> ~a ~@f))))
