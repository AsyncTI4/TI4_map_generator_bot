(in-package #:pgloader.transforms)

(defun ti4-sqlite-millis-or-timestamp-to-timestamp (value)
  (cond
    ((null value) nil)
    ((and (stringp value) (= 0 (length value))) nil)
    ((integerp value) (ti4-millis-to-timestamp value))
    ((and (stringp value) (every #'digit-char-p value))
     (ti4-millis-to-timestamp (parse-integer value)))
    (t value)))

(defun ti4-millis-to-timestamp (millis)
  (let* ((seconds (floor millis 1000))
         (milliseconds (mod millis 1000))
         (unix-epoch-in-universal-time 2208988800))
    (multiple-value-bind (second minute hour day month year)
        (decode-universal-time (+ unix-epoch-in-universal-time seconds) 0)
      (format nil "~4,'0d-~2,'0d-~2,'0d ~2,'0d:~2,'0d:~2,'0d.~3,'0d"
              year month day hour minute second milliseconds))))
