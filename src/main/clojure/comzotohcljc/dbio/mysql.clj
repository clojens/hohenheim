(ns ^{ :doc ""
       :author "kenl" }
  comzotohcljc.dbio.mysql)

(use '[clojure.tools.logging :only (info warn error debug)])
(require '[comzotohcljc.util.core :as CU])
(require '[comzotohcljc.util.str :as SU])
(use '[comzotohcljc.dbio.drivers])
(use '[comzotohcljc.dbio.core])

(deftype MySQL [] DBDriver
  (getId [_] :mysql)
  (getTestString [_] "select version()" ))

;; MySQL
(defmethod getBlobKeyword MySQL [db] "LONGBLOB")
(defmethod getTSKeyword MySQL [db] "TIMESTAMP")
(defmethod getDoubleKeyword MySQL [db] "DOUBLE")
(defmethod getFloatKeyword MySQL [db]  "DOUBLE")

(defmethod genEnd MySQL [db table]
  (str "\n) Type=InnoDB" (genExec db) "\n\n"))

(defmethod genAutoInteger MySQL [db table fld]
  (str (getPad db) (genCol fld) " " (getIntKeyword db) " NOT NULL AUTO_INCREMENT"))

(defmethod genAutoLong MySQL [db table fld]
  (str (getPad db) (genCol fld) " " (getLongKeyword db) " NOT NULL AUTO_INCREMENT"))

(defmethod genDrop MySQL [db table]
  (str "DROP TABLE IF EXISTS " table (genExec db) "\n\n"))


;;(println (getDDL (MySQL.) (make-MetaCache testschema)))

(def ^:private mysql-eof nil)

