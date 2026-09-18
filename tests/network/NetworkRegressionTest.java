package me.srrapero720.watervision.client.screens;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Real loopback HTTP tests, runnable without Forge or external dependencies. */
public final class NetworkRegressionTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        final byte[] data = "0123456789".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        final AtomicInteger gets = new AtomicInteger();
        final AtomicInteger heads = new AtomicInteger();
        final AtomicReference<String> etag = new AtomicReference<>("v1");
        final AtomicBoolean stallHead = new AtomicBoolean();
        final CountDownLatch slowStarted = new CountDownLatch(2);
        final CountDownLatch slowClosed = new CountDownLatch(2);
        final CountDownLatch headStarted = new CountDownLatch(1);
        final CountDownLatch headRelease = new CountDownLatch(1);
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final var executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "NetworkTest-HTTP"); t.setDaemon(true); return t;
        });
        server.setExecutor(executor);
        server.createContext("/video.mp4", exchange -> {
            try (exchange) {
                exchange.getResponseHeaders().set("ETag", etag.get());
                exchange.getResponseHeaders().set("Content-Length", "10");
                exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
                if (exchange.getRequestMethod().equals("HEAD")) {
                    heads.incrementAndGet();
                    if (stallHead.get()) {
                        headStarted.countDown();
                        try { headRelease.await(8, TimeUnit.SECONDS); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }
                    exchange.sendResponseHeaders(200, -1);
                } else {
                    gets.incrementAndGet();
                    final String range = exchange.getRequestHeaders().getFirst("Range");
                    if ("bytes=2-5".equals(range)) {
                        exchange.getResponseHeaders().set("Content-Range", "bytes 2-5/10");
                        exchange.sendResponseHeaders(206, 4);
                        exchange.getResponseBody().write(Arrays.copyOfRange(data, 2, 6));
                    } else {
                        exchange.sendResponseHeaders(200, data.length);
                        exchange.getResponseBody().write(data);
                    }
                }
            }
        });
        server.createContext("/no-ranges.mp4", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, data.length);
                exchange.getResponseBody().write(data);
            }
        });
        server.createContext("/error.mp4", exchange -> {
            try (exchange) { exchange.sendResponseHeaders(503, -1); }
        });
        server.createContext("/slow.mp4", exchange -> {
            try (exchange) {
                if (exchange.getRequestMethod().equals("HEAD")) {
                    exchange.sendResponseHeaders(200, -1);
                    return;
                }
                exchange.sendResponseHeaders(200, 0);
                slowStarted.countDown();
                final byte[] chunk = new byte[8192];
                for (int i = 0; i < 2000; i++) {
                    exchange.getResponseBody().write(chunk);
                    exchange.getResponseBody().flush();
                    try { Thread.sleep(10); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                }
            } catch (IOException expectedOnCancel) {
                slowClosed.countDown();
            }
        });
        final Path directory = Files.createTempDirectory("watervision-network-test");
        server.start();
        final String base = "http://127.0.0.1:" + server.getAddress().getPort();
        final URI remote = URI.create(base + "/video.mp4");
        final VisionMediaCache cache = new VisionMediaCache(directory);
        final HttpClient http = HttpClient.newHttpClient();
        URI proxy = null;
        URI noRanges = null;
        URI slowProxy = null;
        try {
            check(cache.findCached(remote) == null, "Empty cache must return a miss");
            check(heads.get() == 0 && gets.get() == 0, "Cache miss must not contact the host");
            URI local = cache.downloadRemoteToCache(remote);
            check(Arrays.equals(Files.readAllBytes(Path.of(local)), data), "Cached bytes differ");
            check(gets.get() == 1, "Full download must issue one GET");
            check(cache.findCached(remote).equals(local), "Valid cache must be reused");
            check(cache.downloadRemoteToCache(remote).equals(local), "Existing valid download must be reused");
            check(gets.get() == 1, "Cache reuse must not download again");
            etag.set("v2");
            check(cache.findCached(remote) == null, "Changed ETag must invalidate cache lookup");
            cache.downloadRemoteToCache(remote);
            check(gets.get() == 2, "Stale cache must be refreshed exactly once");

            stallHead.set(true);
            long start = System.nanoTime();
            check(cache.findCached(remote).equals(local), "Offline validation must retain usable cache");
            check(Duration.ofNanos(System.nanoTime() - start).toSeconds() < 7, "HEAD validation must be bounded");
            stallHead.set(false);
            headRelease.countDown();

            boolean failed = false;
            try { cache.downloadRemoteToCache(URI.create(base + "/error.mp4")); }
            catch (IOException expected) { failed = true; }
            check(failed, "HTTP errors must fail the download");
            try (var files = Files.list(directory)) {
                check(files.noneMatch(p -> p.toString().endsWith(".download")), "Failed download leaked temporary file");
            }

            int beforeProxy = gets.get();
            proxy = VisionStreamingProxy.proxy(remote);
            check(gets.get() == beforeProxy, "Creating a proxy must not start a background download");
            var ranged = http.send(HttpRequest.newBuilder(proxy).header("Range", "bytes=2-5").build(), HttpResponse.BodyHandlers.ofByteArray());
            check(ranged.statusCode() == 206 && Arrays.equals(ranged.body(), Arrays.copyOfRange(data, 2, 6)), "Range bytes were not preserved");
            check(ranged.headers().firstValue("Content-Range").orElse("").equals("bytes 2-5/10"), "Content-Range lost");
            check(gets.get() == beforeProxy + 1, "One proxy read must cause one upstream GET");
            noRanges = VisionStreamingProxy.proxy(URI.create(base + "/no-ranges.mp4"));
            var unsupported = http.send(HttpRequest.newBuilder(noRanges).build(), HttpResponse.BodyHandlers.ofByteArray());
            check(unsupported.headers().firstValue("Accept-Ranges").isEmpty(), "Proxy must not invent range support");
            VisionStreamingProxy.release(proxy);
            var removed = http.send(HttpRequest.newBuilder(proxy).build(), HttpResponse.BodyHandlers.discarding());
            check(removed.statusCode() == 404, "Released route must reject subsequent reads");

            final AtomicReference<Throwable> downloadOutcome = new AtomicReference<>();
            Thread download = new Thread(() -> {
                try { cache.downloadRemoteToCache(URI.create(base + "/slow.mp4")); }
                catch (Throwable error) { downloadOutcome.set(error); }
            });
            download.start();
            slowProxy = VisionStreamingProxy.proxy(URI.create(base + "/slow.mp4"));
            var streaming = http.send(HttpRequest.newBuilder(slowProxy).build(), HttpResponse.BodyHandlers.ofInputStream());
            check(slowStarted.await(3, TimeUnit.SECONDS), "Slow transfers did not start");
            cache.cancel();
            download.interrupt();
            VisionStreamingProxy.release(slowProxy);
            streaming.body().close();
            download.join(3000);
            check(!download.isAlive() && downloadOutcome.get() instanceof InterruptedException, "Full download did not cancel promptly: alive=" + download.isAlive() + ", outcome=" + downloadOutcome.get());
            check(slowClosed.await(3, TimeUnit.SECONDS), "Upstream transfers survived cancellation");
            try (var files = Files.list(directory)) {
                check(files.noneMatch(p -> p.toString().endsWith(".download")), "Cancellation leaked temporary file");
            }
            cache.invalidate(remote);
            check(cache.findCached(remote) == null, "Unreadable cache invalidation failed");
            System.out.println("PASS: cache priority, validation timeout, single GET, stale cache, failure cleanup, ranges, proxy release and transfer cancellation");
        } finally {
            VisionStreamingProxy.release(proxy);
            VisionStreamingProxy.release(noRanges);
            VisionStreamingProxy.release(slowProxy);
            headRelease.countDown();
            server.stop(0);
            executor.shutdownNow();
            try (var files = Files.walk(directory)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
}
