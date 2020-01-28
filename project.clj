(defproject metabase/crate-driver "1.0.1-crate-jdbc-2.3.0"
  :min-lein-version "2.5.0"

  :dependencies
  [[io.crate/crate-jdbc "2.3.0"
    :exclusions [com.fasterxml.jackson.core/jackson-core
                 org.slf4j/slf4j-api]]]

  :repositories
  [["bintray" "https://dl.bintray.com/crate/crate"]] ; Repo for Crate JDBC driver

  :profiles
  {:provided
   {:dependencies
    [
  [org.clojure/clojure "1.10.1"]
   [org.clojure/core.async "0.4.500"
    :exclusions [org.clojure/tools.reader]]
   [org.clojure/core.match "0.3.0"]                                   ; optimized pattern matching library for Clojure
   [org.clojure/core.memoize "0.7.1"]                                 ; needed by core.match; has useful FIFO, LRU, etc. caching mechanisms
   [org.clojure/data.csv "0.1.4"]                                     ; CSV parsing / generation
   [org.clojure/java.classpath "0.3.0"]                               ; examine the Java classpath from Clojure programs
   [org.clojure/java.jdbc "0.7.9"]                                    ; basic JDBC access from Clojure
   [org.clojure/math.combinatorics "0.1.4"]                           ; combinatorics functions
   [org.clojure/math.numeric-tower "0.0.4"]                           ; math functions like `ceil`
   [org.clojure/tools.logging "0.4.1"]                                ; logging framework
   [org.clojure/tools.namespace "0.2.11"]
   [org.clojure/tools.trace "0.7.10"]                                 ; function tracing
   [amalloy/ring-buffer "1.2.2"
    :exclusions [org.clojure/clojure
                 org.clojure/clojurescript]]                          ; fixed length queue implementation, used in log buffering
   [amalloy/ring-gzip-middleware "0.1.4"]                             ; Ring middleware to GZIP responses if client can handle it
   [aleph "0.4.6" :exclusions [org.clojure/tools.logging]]            ; Async HTTP library; WebSockets
   [bigml/histogram "4.1.3"]                                          ; Histogram data structure
   [buddy/buddy-core "1.5.0"                                          ; various cryptograhpic functions
    :exclusions [commons-codec]]
   [buddy/buddy-sign "3.0.0"]                                         ; JSON Web Tokens; High-Level message signing library
   [cheshire "5.8.1"]                                                 ; fast JSON encoding (used by Ring JSON middleware)
   [clj-http "3.9.1"                                                  ; HTTP client
    :exclusions [commons-codec
                 commons-io
                 slingshot]]
   [clojure.java-time "0.3.2"]                                        ; Java 8 java.time wrapper
   [clojurewerkz/quartzite "2.1.0"                                    ; scheduling library
    :exclusions [c3p0]]
   [colorize "0.1.1" :exclusions [org.clojure/clojure]]               ; string output with ANSI color codes (for logging)
   [com.cemerick/friend "0.2.3"                                       ; auth library
    :exclusions [commons-codec
                 org.apache.httpcomponents/httpclient
                 net.sourceforge.nekohtml/nekohtml
                 ring/ring-core]]
   [com.clearspring.analytics/stream "2.9.6"                          ; Various sketching algorithms
    :exclusions [org.slf4j/slf4j-api
                 it.unimi.dsi/fastutil]]
   [com.draines/postal "2.0.3"]                                       ; SMTP library
   [com.jcraft/jsch "0.1.55"]                                         ; SSH client for tunnels
   [com.google.guava/guava "28.2-jre"]                                ; dep for BigQuery, Spark, and GA. Require here rather than letting different dep versions stomp on each other — see comments on #9697
   [com.h2database/h2 "1.4.197"]                                      ; embedded SQL database
   [com.mattbertolini/liquibase-slf4j "2.0.0"]                        ; Java Migrations lib logging. We don't actually use this AFAIK (?)
   [com.taoensso/nippy "2.14.0"]                                      ; Fast serialization (i.e., GZIP) library for Clojure
   [commons-codec/commons-codec "1.12"]                               ; Apache Commons -- useful codec util fns
   [commons-io/commons-io "2.6"]                                      ; Apache Commons -- useful IO util fns
   [commons-validator/commons-validator "1.6"                         ; Apache Commons -- useful validation util fns
    :exclusions [commons-beanutils
                 commons-digester
                 commons-logging]]
   [compojure "1.6.1" :exclusions [ring/ring-codec]]                  ; HTTP Routing library built on Ring
   [crypto-random "1.2.0"]                                            ; library for generating cryptographically secure random bytes and strings
   [dk.ative/docjure "1.13.0"]                                        ; Excel export
   [environ "1.1.0"]                                                  ; easy environment management
   [hiccup "1.0.5"]                                                   ; HTML templating
   [honeysql "0.9.5" :exclusions [org.clojure/clojurescript]]         ; Transform Clojure data structures to SQL
   [instaparse "1.4.10"]                                              ; Make your own parser
   [io.forward/yaml "1.0.9"                                           ; Clojure wrapper for YAML library SnakeYAML (which we already use for liquidbase)
    :exclusions [org.clojure/clojure
                 org.flatland/ordered
                 org.yaml/snakeyaml]]
   [javax.xml.bind/jaxb-api "2.4.0-b180830.0359"]                     ; add the `javax.xml.bind` classes which we're still using but were removed in Java 11
   [kixi/stats "0.4.4" :exclusions [org.clojure/data.avl]]            ; Various statistic measures implemented as transducers
   [log4j/log4j "1.2.17"                                              ; logging framework. TODO - consider upgrading to Log4j 2 -- see https://logging.apache.org/log4j/log4j-2.6.1/manual/migration.html
    :exclusions [javax.mail/mail
                 javax.jms/jms
                 com.sun.jdmk/jmxtools
                 com.sun.jmx/jmxri]]
   [me.raynes/fs "1.4.6"]                                             ; Filesystem tools
   [medley "1.2.0"]                                                   ; lightweight lib of useful functions
   [metabase/connection-pool "1.1.1"]                                 ; simple wrapper around C3P0. JDBC connection pools
   [metabase/mbql "1.4.3"]                                            ; MBQL language schema & util fns
   [metabase/throttle "1.0.2"]                                        ; Tools for throttling access to API endpoints and other code pathways
   [net.sf.cssbox/cssbox "4.12" :exclusions [org.slf4j/slf4j-api]]    ; HTML / CSS rendering
   [org.apache.commons/commons-lang3 "3.9"]                           ; helper methods for working with java.lang stuff
   [org.clojars.pntblnk/clj-ldap "0.0.16"]                            ; LDAP client
   [org.eclipse.jetty/jetty-server "9.4.15.v20190215"]                ; We require JDK 8 which allows us to run Jetty 9.4, ring-jetty-adapter runs on 1.7 which forces an older version
   [org.flatland/ordered "1.5.7"]                                     ; ordered maps & sets
   [org.liquibase/liquibase-core "3.6.3"                              ; migration management (Java lib)
    :exclusions [ch.qos.logback/logback-classic]]
   [org.mariadb.jdbc/mariadb-java-client "2.5.1"]                     ; MySQL/MariaDB driver
   [org.postgresql/postgresql "42.2.8"]                               ; Postgres driver
   [org.slf4j/slf4j-log4j12 "1.7.25"]                                 ; abstraction for logging frameworks -- allows end user to plug in desired logging framework at deployment time
   [org.tcrawley/dynapath "1.0.0"]                                    ; Dynamically add Jars (e.g. Oracle or Vertica) to classpath
   [org.threeten/threeten-extra "1.5.0"]                               ; extra Java 8 java.time classes like DayOfMonth and Quarter
   [org.yaml/snakeyaml "1.23"]                                        ; YAML parser (required by liquibase)
   [potemkin "0.4.5"]                                                 ; utility macros & fns
   [pretty "1.0.1"]                                                   ; protocol for defining how custom types should be pretty printed
   [prismatic/schema "1.1.11"]                                        ; Data schema declaration and validation library
   [puppetlabs/i18n "0.8.0"]                                          ; Internationalization library
   [redux "0.1.4"]                                                    ; Utility functions for building and composing transducers
   [ring/ring-core "1.7.1"]
   [ring/ring-jetty-adapter "1.7.1"]                                  ; Ring adapter using Jetty webserver (used to run a Ring server for unit tests)
   [ring/ring-json "0.4.0"]                                           ; Ring middleware for reading/writing JSON automatically
   [stencil "0.5.0"]                                                  ; Mustache templates for Clojure
   [toucan "1.15.0" :exclusions [org.clojure/java.jdbc honeysql]]     ; Model layer, hydration, and DB utilities
   [weavejester/dependency "0.2.1"]                                   ; Dependency graphs and topological sorting
   [metabase-core/metabase-core "1.0.0-SNAPSHOT"]
    ]}

   :uberjar
   {:auto-clean    true
    :aot           :all
    :omit-source   true
    :javac-options ["-target" "1.8", "-source" "1.8"]
    :target-path   "target/%s"
    :uberjar-name  "crate.metabase-driver.jar"}})
