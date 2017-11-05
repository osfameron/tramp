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

(deftest test-form->function
  (testing "single iFN returned as-is"
    (is (= inc (form->function inc)))
    (is (= :foo (form->function :foo)))
    (is (= 2 ((form->function #(inc %)) 1))))
  (testing "List with function and no arguments returns that function"
    (is (= inc (form->function (list inc))))
    (is (= :foo (form->function (list :foo)))))
  (testing "List with function and multiple arguments behaves as ->"
    (is (= [1 2] ((eval (form->function (list vector 2))) 1))))
  (testing "% can be used to change location of argument"
    (is (= [1 2] ((eval (form->function (list vector 1 '%))) 2)))))

(deftest test-tramp->
  (testing "tramp-> with no function"
    (is (= 1 (tramp-> 1))))
  (testing "tramp-> with non-form function"
    (is (= 2 (tramp-> 1 inc))))
  (testing "tramp-> with single form function"
    (is (= 2 (tramp-> 1 (inc)))))
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

(deftest test-return
  (is (= :return (tramp-> 1
                          inc
                          (return :return)
                          inc))))
