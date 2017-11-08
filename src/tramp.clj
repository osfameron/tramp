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
  "Splits around the item matching the predicate.
  Takes 3rd arg for whether to include the matching item"
  [p coll inclusive?]
  (let [[a [_ & b-exc :as b-inc]] (split-with (complement p) coll)]
    [a (if inclusive? b-inc b-exc)]))

(def %? #{'%})
(def !? #{'!})

(defn seq->template [[f & args]]
  (if (seq? args)
    (let [[a b] (split-on %? args true)] 
      (if (seq b)
        (concat [f] a b)
        (concat [f '%] a)))
    [f]))

(defn form->template [form]
  (if (seq? form)
    (seq->template form)
    [form]))

(defn template->function [[f & args]]
  (if (seq args)
    (let [[a b] (split-on %? args false)] 
      `(fn [~'arg] (~f ~@a ~'arg ~@b)))
    f))

(def form->function (comp template->function form->template))

(defn tramp->* 
  "function backend to tramp-> macro"
  [v forms]
  (let [[a b] (split-on !? forms false)
        pure (map form->function a)
        jumped (when (seq b)
                 (let [[b & bs] b
                       [f & args] (form->template b)
                       [a b] (split-on %? args false)] 
                   `((fn [arg#]
                       (let [args# (concat [~@a] [arg#] [~@b])
                             next-fn# (fn [~'next-arg] ~(tramp->* 'next-arg bs))]
                         (jump ~f args# next-fn#))))))] 
    `(reduce (comp eval reverse list)  ~v [~@(concat pure jumped)])))

(defmacro tramp-> 
  "Threading macro like `->`
  Takes a value v and a number of forms.

  The forms may be prefixed by `!` in which case instead of proceeding
  they return a `jump` function which will continue the thread when called.

  You can call a tramp-> thread using `trampoline`:
    (trampoline (tramp-> 1 !(inc) !(inc) !(inc))) => 3"
  [v & forms]
  (let [result (tramp->* v forms)]
    result))

(defn return [_ v]
  (reduced v))

;; e.g. (guard odd?) in a thread is equivalent to:
;;      (#(if (odd? %) % (reduced nil)))
(defn guard [v pred]
  (if (pred v)
    v
    (reduced nil)))

(defmacro is-result [a expected]
  `(test/is (= ~expected ~a)))

(defn step! [a & args]
  (apply a args))

(defmacro is-fn [a f]
  `(do
     (test/is (= ~f (:fn (meta ~a))))
     ~a))

(defmacro is-args [a args]
  `(do
     (test/is (= [~@args] (:args (meta ~a))))
     ~a))

(defmacro is-arg [a arg]
  `(do
     (test/is (= [~arg] (:args (meta ~a))))
     ~a))

(defmacro if-> [a p t & [f]]
  `(#(if (~p ~a)
       (tramp-> ~a ~@t)
       (tramp-> ~a ~@f))))
