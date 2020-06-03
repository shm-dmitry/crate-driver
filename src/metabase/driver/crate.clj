(ns metabase.driver.crate
  (:refer-clojure :exclude [second])
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [honeysql
             [core :as hsql]
             [format :as hformat]]
            [metabase.driver :as driver]
            [metabase.driver.common :as driver.common]
            [metabase.driver.sql-jdbc
             [connection :as sql-jdbc.conn]
             [sync :as sql-jdbc.sync]]
            [metabase.driver.sql.query-processor :as sql.qp]
            [metabase.util
             [date-2 :as du]
             [honeysql-extensions :as hx]
             [i18n :refer [trs]]])
  (:import [java.sql DatabaseMetaData Timestamp]))

(driver/register! :crate, :parent :sql-jdbc)

;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                              HoneySQL Extensions                                               |
;;; +----------------------------------------------------------------------------------------------------------------+

;; `:crate` quote style that correctly quotes nested column identifiers
(defn- str-insert
  "Insert C in string S at index I."
  [s c i]
  (str c (subs s 0 i) c (subs s i)))

(defn- crate-column-identifier
  [^CharSequence s]
  (let [idx (str/index-of s "[")]
    (if (nil? idx)
      (str \" s \")
      (str-insert s "\"" idx))))

(let [quote-fns @(resolve 'hformat/quote-fns)]
  (intern 'honeysql.format 'quote-fns
          (assoc quote-fns :crate crate-column-identifier)))

;; register the try_cast function with HoneySQL
;; (hsql/format (hsql/call :crate-try-cast :TIMESTAMP :field)) -> "try_cast(field as TIMESTAMP)"
(defmethod hformat/fn-handler "crate-try-cast" [_ klass expr]
  (str "try_cast(" (hformat/to-sql expr) " as " (name klass) ")"))


;;; ------------------------------------------------ datetime helpers ------------------------------------------------

(def ^:private second 1000)
(def ^:private minute (* 60 second))
(def ^:private hour   (* 60 minute))
(def ^:private day    (* 24 hour))
(def ^:private week   (* 7 day))
(def ^:private year   (* 365 day))
(def ^:private month  (Math/round (float (/ year 12))))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                          metabase.driver method impls                                          |
;;; +----------------------------------------------------------------------------------------------------------------+

(defmethod driver/supports? [:crate :foreign-keys] [_ _] false)

(defmethod driver.common/current-db-time-date-formatters :crate [_]
  (driver.common/create-db-time-formatters "yyyy-MM-dd HH:mm:ss.SSSSSSZ"))

(defmethod driver.common/current-db-time-native-query :crate [_]
  "select DATE_FORMAT(current_timestamp, '%Y-%m-%d %H:%i:%S.%fZ')")

(defmethod driver/current-db-time :crate [& args]
  (apply driver.common/current-db-time args))

(defn- sql-interval [dt unit amount]
  (format "%d + %d" (dt) (* unit amount)))

(defmethod driver/date-add :crate [_ dt unit amount]
  (case unit
    :quarter (recur nil dt :month (hx/* amount 3))
    :year    (hsql/raw (sql-interval dt year   amount))
    :month   (hsql/raw (sql-interval dt month  amount))
    :week    (hsql/raw (sql-interval dt week   amount))
    :day     (hsql/raw (sql-interval dt day    amount))
    :hour    (hsql/raw (sql-interval dt hour   amount))
    :minute  (hsql/raw (sql-interval dt minute amount))
    :second  (hsql/raw (sql-interval dt second amount))))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                        metabase.driver.sql method impls                                        |
;;; +----------------------------------------------------------------------------------------------------------------+

(defmethod sql.qp/quote-style :crate [_] :crate)

(defmethod sql.qp/current-datetime-fn :crate [_] (hsql/call :current_timestamp 3))

(defmethod sql.qp/field->alias :crate [& _] nil)

(defmethod sql.qp/unix-timestamp->timestamp :crate [driver seconds-or-milliseconds field-or-value]
  (case seconds-or-milliseconds
    :seconds      (recur nil (hx/* field-or-value 1000) :milliseconds)
    :milliseconds (hsql/call :crate-try-cast :TIMESTAMP field-or-value)))


;;; ------------------------------------------- sql.qp/date implementation -------------------------------------------

(defn- date-trunc
  "date_trunc('interval', timezone, timestamp): truncates a timestamp to a given interval"
  [unit expr]
  (let [timezone (get-in sql.qp/*query* [:settings :report-timezone])]
    (if (nil? timezone)
      (hsql/call :date_trunc (hx/literal unit) expr)
      (hsql/call :date_trunc (hx/literal unit) timezone expr))))

(defn- date-format
  "date_format('format_string', timezone, timestamp): formats the timestamp as string"
  [fmt expr]
  (let [timezone (get-in sql.qp/*query* [:settings :report-timezone])]
    (if (nil? timezone)
      (hsql/call :date_format fmt expr)
      (hsql/call :date_format fmt timezone expr))))

(defn- extract
  "extract(field from expr): extracts subfields of a timestamp"
  [unit expr]
  (if-not (= unit :day_of_week)
    (hsql/call :extract unit expr)
    ;; Crate DOW starts with Monday (1) to Sunday (7)
    (hx/+ (hx/mod (hsql/call :extract unit expr)
                  7)
          1)))

(def ^:private extract-integer (comp hx/->integer extract))

(defn- expr->literal [expr]
  (if (instance? Timestamp expr)
    (hx/literal (du/format-sql expr))
    expr))

(defmethod sql.qp/date [:crate :default] [_ _ expr]
  (date-format (str "%Y-%m-%d %H:%i:%s") (expr->literal expr)))

(defmethod sql.qp/date [:crate :second] [_ _ expr]
  (date-format (str "%Y-%m-%d %H:%i:%s") (date-trunc :second (expr->literal expr))))

(defmethod sql.qp/date [:crate :minute] [_ _ expr]
  (date-format (str "%Y-%m-%d %H:%i:%s") (date-trunc :minute (expr->literal expr))))

(defmethod sql.qp/date [:crate :minute-of-hour] [_ _ expr]
  (extract-integer :minute (expr->literal expr)))

(defmethod sql.qp/date [:crate :hour] [_ _ expr]
  (date-format (str "%Y-%m-%d %H:%i:%s") (date-trunc :hour (expr->literal expr))))

(defmethod sql.qp/date [:crate :hour-of-day] [_ _ expr]
  (extract-integer :hour (expr->literal expr)))

(defmethod sql.qp/date [:crate :day] [_ _ expr]
  (date-format (str "%Y-%m-%d") (date-trunc :day (expr->literal expr))))

(defmethod sql.qp/date [:crate :day-of-week] [_ _ expr]
  (extract-integer :day_of_week (expr->literal expr)))

(defmethod sql.qp/date [:crate :day-of-month] [_ _ expr]
  (extract-integer :day_of_month (expr->literal expr)))

(defmethod sql.qp/date [:crate :day-of-year] [_ _ expr]
  (extract-integer :day_of_year (expr->literal expr)))

;; Crate weeks start on Monday, so shift this date into the proper bucket and then decrement the resulting day
(defmethod sql.qp/date [:crate :week] [_ _ expr]
  (date-format (str "%Y-%m-%d") (hx/- (date-trunc :week (hx/+ (expr->literal expr) day)) day)))

(defmethod sql.qp/date [:crate :week-of-year] [_ _ expr]
  (extract-integer :week (expr->literal expr)))

(defmethod sql.qp/date [:crate :month] [_ _ expr]
  (date-format (str "%Y-%m-%d") (date-trunc :month (expr->literal expr))))

(defmethod sql.qp/date [:crate :month-of-year] [_ _ expr]
  (extract-integer :month (expr->literal expr)))

(defmethod sql.qp/date [:crate :quarter] [_ _ expr]
  (date-format (str "%Y-%m-%d") (date-trunc :quarter (expr->literal expr))))

(defmethod sql.qp/date [:crate :quarter-of-year] [_ _ expr]
  (extract-integer :quarter (expr->literal expr)))

(defmethod sql.qp/date [:crate :year] [_ _ expr]
  (extract-integer :year (expr->literal expr)))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                     metabase.driver.sql-jdbc method impls                                      |
;;; +----------------------------------------------------------------------------------------------------------------+

;;; ---------------------------------------------- sql-jdbc.connection -----------------------------------------------

(defmethod sql-jdbc.conn/connection-details->spec :crate [_ {:keys [hosts], :as details}]
  (merge
   {:classname   "io.crate.client.jdbc.CrateDriver"
    :subprotocol "crate"
    :subname     (str "//" hosts)
    :user        "crate"}
   (dissoc details :hosts)))


;;; ------------------------------------------------- sql-jdbc.sync --------------------------------------------------

;; Map of Crate column types -> Field base types
;; See https://crate.io/docs/reference/sql/data_types.html
(defmethod sql-jdbc.sync/database-type->base-type :crate [_ database-type]
  ({:integer         :type/Integer
    :string          :type/Text
    :boolean         :type/Boolean
    :byte            :type/Integer
    :short           :type/Integer
    :long            :type/BigInteger
    :float           :type/Float
    :double          :type/Float
    :ip              :type/*
    :timestamp       :type/DateTime
    :geo_shape       :type/Dictionary
    :geo_point       :type/Array
    :object          :type/Dictionary
    :array           :type/Array
    :object_array    :type/Array
    :string_array    :type/Array
    :integer_array   :type/Array
    :float_array     :type/Array
    :boolean_array   :type/Array
    :byte_array      :type/Array
    :timestamp_array :type/Array
    :short_array     :type/Array
    :long_array      :type/Array
    :double_array    :type/Array
    :ip_array        :type/Array
    :geo_shape_array :type/Array
    :geo_point_array :type/Array} database-type))

(defn- describe-table-sql [schema table-name]
  ;; clojure jdbc can't handle fields of type "object_array" atm
  (format "select column_name, data_type as type_name
           from information_schema.columns
           where table_name like '%s' and table_schema like '%s'
           and data_type != 'object_array'" table-name schema))

(defn- describe-table-database-type->base-type [driver database-type]
  (or (sql-jdbc.sync/database-type->base-type driver (keyword database-type))
      (do (log/warn (trs "Dont know how to map column type {0} to a Field base_type, falling back to :type/*."
                         database-type))
          :type/*)))

(defn- describe-table-fields [driver database {schema :schema, table-name :name}]
  (let [columns (jdbc/query (sql-jdbc.conn/db->pooled-connection-spec database)
                            [(describe-table-sql schema table-name)])]
    (set (for [{column-name :column_name, database-type :type_name} columns]
           {:name          column-name
            :database-type database-type
            :base-type     (describe-table-database-type->base-type driver database-type)}))))

(defn- add-table-pks
  [^DatabaseMetaData metadata, table]
  (let [pks (->> (.getPrimaryKeys metadata nil nil (:name table))
                 jdbc/result-set-seq
                 (mapv :column_name)
                 set)]
    (update table :fields (fn [fields]
                            (set (for [field fields]
                                   (if-not (contains? pks (:name field))
                                     field
                                     (assoc field :pk? true))))))))

(defmethod driver/describe-table :crate [driver database table]
  (jdbc/with-db-metadata [metadata (sql-jdbc.conn/db->pooled-connection-spec database)]
    (->> (describe-table-fields driver database table)
         (assoc (select-keys table [:name :schema]) :fields)
         ;; find PKs and mark them
         (add-table-pks metadata))))
