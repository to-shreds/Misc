These JVM tests inject `HttpsURLConnection` fixtures. They check validation before connection creation, transport settings, fixed-length POST bodies, cancellation, timeout, callback lifecycle, response bounds, concurrency, and absence of application retries. They do not test a live account, a real Hyundai TLS connection, or vehicle behavior.

The app uses Android's built-in `org.json`, without bundling a third-party JSON library. For this JVM fixture suite, provide a genuine JSON runtime with `JSON_JAR`. The suite was run with the published `org.json:json:20240303` test-only jar from Maven Central:

```
JAVA_HOME=/path/to/jdk JSON_JAR=/path/to/json-20240303.jar sh tests/transport/run-transport-tests.sh
```

The tests use only synthetic values. They perform no network requests.
