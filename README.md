This reproduces https://github.com/ClickHouse/clickhouse-java/issues/1741 on:
```
❯ java --version
openjdk 21.0.3 2024-04-16
OpenJDK Runtime Environment (Red_Hat-21.0.3.0.9-1) (build 21.0.3+9)
OpenJDK 64-Bit Server VM (Red_Hat-21.0.3.0.9-1) (build 21.0.3+9, mixed mode, sharing)

❯ grep PRETTY /etc/os-release
PRETTY_NAME="Fedora Linux 40 (KDE Plasma)"
```

# Run

Execute `./bin/start ${ENDPOINT} ${USERNAME} ${PASSWORD} ${NUM_CONCURRENT_REQUESTS} ${MAX_THREADS_PER_CLIENT}` in a terminal,
where you replace `${ENDPOINT}` with the URL to your ClickHouse instance, e.g. https://example.clickhouse.cloud:8443,
`${USERNAME}` and `${PASSWORD}` with the username and password for that instance, and `${NUM_CONCURRENT_REQUESTS}` and
`${MAX_THREADS_PER_CLIENT}` with integers.  Both numbers, `NUM_CONCURRENT_REQUESTS` and `MAX_THREADS_PER_CLIENT`, are
command line parameters.

## Happy path

When you run `./bin/start ... 16 0` or `./bin/start ... 17 1`, you will see:
```
Spawning threads...
Wait for all threads:
Waiting for thread 0
Waiting for thread 1
Waiting for thread 2
Waiting for thread 3
Waiting for thread 4
Waiting for thread 5
Waiting for thread 6
Waiting for thread 7
Waiting for thread 8
Waiting for thread 9
Waiting for thread 10
Waiting for thread 11
Waiting for thread 12
Waiting for thread 13
Waiting for thread 14
Waiting for thread 15
Collected all threads!
```

## Broken path

When you run `./bin/start ... 17 0`, you will see:
```
Spawning more than 16 requests without separate thread pools per-client will break the application.
Pass max-threads-per-client > 0 to stay safe; 1 thread per client is enough.
Or reduce num-concurrent-requests < 16.
Spawning threads...
Wait for all threads:
Waiting for thread 0
ERROR: #error {
 :cause nil
 :via
 [{:type java.util.concurrent.ExecutionException
   :message com.clickhouse.client.ClickHouseException: Code: 159. Execution timed out
   :at [java.util.concurrent.FutureTask report FutureTask.java 122]}
  {:type com.clickhouse.client.ClickHouseException
   :message Code: 159. Execution timed out
   :at [com.clickhouse.client.ClickHouseException of ClickHouseException.java 147]}
  {:type java.util.concurrent.TimeoutException
   :message nil
   :at [java.util.concurrent.CompletableFuture timedGet CompletableFuture.java 1960]}]
 :trace
 [[java.util.concurrent.CompletableFuture timedGet CompletableFuture.java 1960]
  [java.util.concurrent.CompletableFuture get CompletableFuture.java 2095]
  [com.clickhouse.client.ClickHouseClient executeAndWait ClickHouseClient.java 878]
  [ch_timeout_mwe$execute_BANG_ invokeStatic ch_timeout_mwe.clj 34]
  [ch_timeout_mwe$execute_BANG_ invoke ch_timeout_mwe.clj 31]
  [ch_timeout_mwe$ping invokeStatic ch_timeout_mwe.clj 40]
  [ch_timeout_mwe$ping invoke ch_timeout_mwe.clj 36]
  [ch_timeout_mwe$_main$iter__158__162$fn__163$fn__164$fn__165 invoke ch_timeout_mwe.clj 56]
  [clojure.core$binding_conveyor_fn$fn__5823 invoke core.clj 2047]
  [clojure.lang.AFn call AFn.java 18]
  [java.util.concurrent.FutureTask run FutureTask.java 317]
  [java.util.concurrent.ThreadPoolExecutor runWorker ThreadPoolExecutor.java 1144]
  [java.util.concurrent.ThreadPoolExecutor$Worker run ThreadPoolExecutor.java 642]
  [java.lang.Thread run Thread.java 1583]]}
[...]
Collected all threads!
We're broken now, and no request will succeed from here on -- they all time out. The only cure is restarting the application.
Execution error (TimeoutException) at java.util.concurrent.CompletableFuture/timedGet (CompletableFuture.java:1960).
null
```

In this case you will not see `This will never be reached.`

## Missing command line arguments

If you fail to provide the command line args, you will see an output like the following:
```
Execution error (ArityException) at clojure.main/main (main.java:40).
Wrong number of args (0) passed to: ch-timeout-mwe/-main
```

In this case make sure you provide 5 arguments to `./bin/start`, as described above.
