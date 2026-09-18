package me.srrapero720.watervision.client.screens;

import me.srrapero720.watervision.WaterVision;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Properties;

/** Disk cache used only before streaming or after streaming has been stopped. */
final class VisionMediaCache {
    private static final String BUILD_TAG = "sequential-cache-001";
    private final Path directory;

    VisionMediaCache(final Path directory) {
        this.directory = directory;
    }

    URI findCached(final URI remoteUri) throws IOException, InterruptedException, NoSuchAlgorithmException {
        final Path cacheFile = this.cacheFile(this.directory, remoteUri);
        // A cache miss must never delay native streaming with a metadata request.
        if (!Files.isRegularFile(cacheFile) || Files.size(cacheFile) == 0L) return null;
        final CacheMetadata remoteMetadata = this.fetchRemoteMetadata(remoteUri);
        final CacheMetadata cachedMetadata = this.readCacheMetadata(this.metadataFile(this.directory, remoteUri));
        if (remoteMetadata == null || (cachedMetadata != null && cachedMetadata.matches(remoteMetadata, Files.size(cacheFile)))) {
            return cacheFile.toUri();
        }
        return null;
    }

    void invalidate(final URI remoteUri) throws IOException, NoSuchAlgorithmException {
        Files.deleteIfExists(this.cacheFile(this.directory, remoteUri));
        Files.deleteIfExists(this.metadataFile(this.directory, remoteUri));
    }

    URI downloadRemoteToCache(final URI remoteUri) throws IOException, InterruptedException, NoSuchAlgorithmException {
        final Path cacheDirectory = this.directory;
        Files.createDirectories(cacheDirectory);
        final Path cacheFile = this.cacheFile(cacheDirectory, remoteUri);
        final Path metadataFile = this.metadataFile(cacheDirectory, remoteUri);
        final CacheMetadata remoteMetadata = this.fetchRemoteMetadata(remoteUri);

        if (Files.isRegularFile(cacheFile) && Files.size(cacheFile) > 0L) {
            final long cachedSize = Files.size(cacheFile);
            final CacheMetadata cachedMetadata = this.readCacheMetadata(metadataFile);
            if (remoteMetadata == null) return cacheFile.toUri();
            if (cachedMetadata != null && cachedMetadata.matches(remoteMetadata, cachedSize)) return cacheFile.toUri();
            Files.deleteIfExists(cacheFile);
            Files.deleteIfExists(metadataFile);
        }

        final Path tempFile = cacheDirectory.resolve(cacheFile.getFileName().toString() + "." + System.nanoTime() + ".download");
        Files.deleteIfExists(tempFile);
        WaterVision.LOGGER.info("WaterVision downloading video to cache [{}]: uri={}, file={}", BUILD_TAG, remoteUri, cacheFile);
        final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(5)).build();
        final HttpRequest request = HttpRequest.newBuilder(remoteUri)
                .timeout(Duration.ofMinutes(10))
                .GET()
                .header("User-Agent", "WaterVision/0.1 cache-fallback")
                .header("Accept", "video/mp4,video/*,*/*")
                .build();
        try {
            final HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(tempFile));
            if (response.statusCode() != 200) {
                Files.deleteIfExists(tempFile);
                throw new IOException("HTTP " + response.statusCode() + " while downloading " + remoteUri);
            }
            final CacheMetadata downloadedMetadata = this.metadataFromResponse(response);
            final long downloadedBytes = Files.size(tempFile);
            if (downloadedMetadata.contentLength() >= 0L && downloadedBytes != downloadedMetadata.contentLength()) {
                Files.deleteIfExists(tempFile);
                throw new IOException("Incomplete download for " + remoteUri + ": got " + downloadedBytes + " bytes, expected " + downloadedMetadata.contentLength());
            }
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Video download cancelled");
            try {
                Files.move(tempFile, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (final AtomicMoveNotSupportedException exception) {
                Files.move(tempFile, cacheFile, StandardCopyOption.REPLACE_EXISTING);
            }
            this.writeCacheMetadata(metadataFile, remoteUri, downloadedMetadata);
            WaterVision.LOGGER.info("WaterVision cache download completed [{}]: uri={}, file={}, bytes={}, metadata={}",
                    BUILD_TAG, remoteUri, cacheFile, Files.size(cacheFile), downloadedMetadata);
            return cacheFile.toUri();
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private Path cacheFile(final Path cacheDirectory, final URI remoteUri) throws NoSuchAlgorithmException {
        return cacheDirectory.resolve("media-" + this.sha256(remoteUri.toString()) + this.safeExtension(remoteUri));
    }

    private Path metadataFile(final Path cacheDirectory, final URI remoteUri) throws NoSuchAlgorithmException {
        return cacheDirectory.resolve(this.cacheFile(cacheDirectory, remoteUri).getFileName().toString() + ".meta");
    }

    private CacheMetadata fetchRemoteMetadata(final URI remoteUri) throws InterruptedException {
        try {
            final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(5)).build();
            final HttpRequest request = HttpRequest.newBuilder(remoteUri)
                    .timeout(Duration.ofSeconds(5))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .header("User-Agent", "WaterVision/0.1 cache-validator")
                    .header("Accept", "video/mp4,video/*,*/*")
                    .build();
            final HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                WaterVision.LOGGER.warn("WaterVision remote metadata check failed [{}]: status={}, uri={}", BUILD_TAG, response.statusCode(), remoteUri);
                return null;
            }
            return this.metadataFromResponse(response);
        } catch (final InterruptedException exception) {
            throw exception;
        } catch (final Exception exception) {
            WaterVision.LOGGER.warn("WaterVision remote metadata check failed [{}]: uri={}", BUILD_TAG, remoteUri, exception);
            return null;
        }
    }

    private CacheMetadata metadataFromResponse(final HttpResponse<?> response) {
        final String etag = response.headers().firstValue("ETag").orElse("");
        final String lastModified = response.headers().firstValue("Last-Modified").orElse("");
        final long contentLength = this.parseLong(response.headers().firstValue("Content-Length").orElse("-1"), -1L);
        return new CacheMetadata(etag, lastModified, contentLength);
    }

    private CacheMetadata readCacheMetadata(final Path metadataFile) {
        if (!Files.isRegularFile(metadataFile)) return null;
        final Properties properties = new Properties();
        try (final var input = Files.newInputStream(metadataFile)) {
            properties.load(input);
            final String etag = properties.getProperty("etag", "");
            final String lastModified = properties.getProperty("lastModified", "");
            final long contentLength = this.parseLong(properties.getProperty("contentLength", "-1"), -1L);
            return new CacheMetadata(etag, lastModified, contentLength);
        } catch (final IOException exception) {
            WaterVision.LOGGER.warn("WaterVision cache metadata read failed [{}]: file={}", BUILD_TAG, metadataFile, exception);
            return null;
        }
    }

    private void writeCacheMetadata(final Path metadataFile, final URI remoteUri, final CacheMetadata metadata) throws IOException {
        final Properties properties = new Properties();
        properties.setProperty("url", remoteUri.toString());
        if (metadata != null) {
            properties.setProperty("etag", metadata.etag());
            properties.setProperty("lastModified", metadata.lastModified());
            properties.setProperty("contentLength", Long.toString(metadata.contentLength()));
        }
        try (final var output = Files.newOutputStream(metadataFile)) {
            properties.store(output, "WaterVision cache metadata");
        }
    }

    private long parseLong(final String value, final long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (final Exception ignored) {
            return fallback;
        }
    }

    private String safeExtension(final URI remoteUri) {
        final String path = remoteUri.getPath();
        if (path == null) return ".mp4";
        final int slash = path.lastIndexOf('/');
        final int dot = path.lastIndexOf('.');
        if (dot <= slash || dot < 0 || dot >= path.length() - 1) return ".mp4";
        final String extension = path.substring(dot).toLowerCase(Locale.ROOT);
        if (extension.length() > 12 || !extension.matches("\\.[a-z0-9]+")) return ".mp4";
        return extension;
    }

    private String sha256(final String value) throws NoSuchAlgorithmException {
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private record CacheMetadata(String etag, String lastModified, long contentLength) {
        private boolean matches(final CacheMetadata remote, final long cachedFileSize) {
            if (remote == null) return false;
            if (remote.contentLength() >= 0L && cachedFileSize != remote.contentLength()) return false;
            if (!remote.etag().isBlank()) return remote.etag().equals(this.etag());
            if (!remote.lastModified().isBlank()) return remote.lastModified().equals(this.lastModified());
            return remote.contentLength() >= 0L && cachedFileSize == remote.contentLength();
        }
    }

}
