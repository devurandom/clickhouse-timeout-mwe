(ns ch-timeout-mwe
 (:import
   (com.clickhouse.client ClickHouseClient ClickHouseNode ClickHouseNodeSelector ClickHouseProtocol ClickHouseRequest)
   (com.clickhouse.client.config ClickHouseClientOption ClickHouseDefaults)
   (com.clickhouse.client.http.config ClickHouseHttpOption HttpConnectionProvider)
   (com.clickhouse.config ClickHouseOption)
   (com.clickhouse.data ClickHouseFormat)
   (java.net URI)
   (java.util Map)))

(defn- make-node
 ^ClickHouseNode [^String endpoint username password]
 (ClickHouseNode/of endpoint ^Map (-> {ClickHouseDefaults/USER     username
                                       ClickHouseDefaults/PASSWORD password}
                                      (update-keys #(.getKey ^ClickHouseOption %)))))

(defn- make-client
 ^ClickHouseClient [^String endpoint max-threads]
  (let [protocol (ClickHouseProtocol/fromUriScheme (.getScheme (URI. endpoint)))]
    (.build (doto (ClickHouseClient/builder)
              (.options {ClickHouseHttpOption/CONNECTION_PROVIDER      HttpConnectionProvider/HTTP_CLIENT
                         ClickHouseClientOption/MAX_THREADS_PER_CLIENT (int max-threads)})
              (.nodeSelector (ClickHouseNodeSelector/of protocol nil))))))

(defn- make-read-request
 ^ClickHouseRequest [^ClickHouseClient client ^ClickHouseNode node ^String query]
 (doto (.read client node)
   (.format ClickHouseFormat/RowBinaryWithNamesAndTypes)
   (.query query)))

(defn- execute!
 [^ClickHouseClient client nodes query]
 (let [request (make-read-request client nodes query)]
   (.close (.executeAndWait client request))))

(defn- ping
 [endpoint username password max-threads]
 (let [nodes (make-node endpoint username password)]
   (with-open [client (make-client endpoint max-threads)]
     (execute! client nodes "SELECT 1=1"))))

(defn -main
 [endpoint username password num-concurrent-requests max-threads-per-client]
 (let [num-concurrent-requests (cond-> num-concurrent-requests
                                       (string? num-concurrent-requests) parse-long)
       max-threads-per-client  (cond-> max-threads-per-client
                                       (string? max-threads-per-client) parse-long)
       hang-predicted?         (and (< max-threads-per-client 1)
                                    (< 16 num-concurrent-requests))]
   (when hang-predicted?
     (println "Spawning more than 16 requests without separate thread pools per-client will break the application.")
     (println "Pass max-threads-per-client > 0 to stay safe; 1 thread per client is enough.")
     (println "Or reduce num-concurrent-requests < 16."))
   (println "Spawning threads...")
   (let [futures (for [i (range num-concurrent-requests)]
                   [i (future (ping endpoint username password max-threads-per-client))])]
     (println "Wait for all threads:")
     (doseq [[i f] futures]
       (try
         (println "Waiting for thread" i)
         (deref f)
         (catch Exception e
           (println "ERROR:" e)))))
   (println "Collected all threads!")
   (when hang-predicted?
     (println "We're broken now, and no request will succeed from here on -- they all time out. The only cure is restarting the application."))
   (ping endpoint username password max-threads-per-client)
   (when hang-predicted?
     (println "This will never be reached."))))
