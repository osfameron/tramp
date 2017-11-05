(ns tramp-test
  (:require [clojure.test :refer :all]
            [tramp :refer :all]))

(deftest test-jump
  (let [j (jump inc [1] str)]
    (testing "Result of calling jump is a function, with correct metadata"
      (is (fn? j))
      (is (= inc (:fn (meta j))))
      (is (= [1] (:args (meta j)))))
    (testing "Calling function with 0 args returns (f args) passed into next-fn"
      (is (= "2" (j))))
    (testing "Calling function with 1 override arg returns the override passed into next-fn"
      (is (= "3" (j 3))))))

(deftest test-tramp->
  (testing "tramp-> is just a thread if no forms have ! prefix"
    (is (= 4 (tramp-> 1 (inc) (inc) (inc)))))

  (let [t (tramp-> 1 (inc) !(inc) (str))]
    (testing "Result of tramp-> with ! prefix is a jump function"
      (is (fn? t))
      (is (= inc (:fn (meta t))))
      (is (= [2] (:args (meta t))))
      (is (= "3" (t)))
      (is (= "10" (t 10)))))

  (let [f (fn [] (tramp-> 1 !(inc) !(inc) !(str)))]
    (testing "Each successive evaluation is trampolined"
      (is (= "3" ((((f))))))
      (is (= "3" (trampoline f))))))
