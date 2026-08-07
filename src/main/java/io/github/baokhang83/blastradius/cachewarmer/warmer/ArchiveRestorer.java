package io.github.baokhang83.blastradius.cachewarmer.warmer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Restores a ZIP archive atomically into an absent directory without allowing path traversal. */
final class ArchiveRestorer {

    private ArchiveRestorer() {
    }

    static void restore(byte[] archive, Path destinationDirectory) throws IOException {
        Path parent = destinationDirectory.getParent();
        if (parent == null) {
            throw new IOException("destination has no parent: " + destinationDirectory);
        }
        Files.createDirectories(parent);
        Path staging = Files.createTempDirectory(parent, ".cache-warmer-");
        boolean restored = false;
        try {
            int files = extract(archive, staging);
            if (files == 0) {
                throw new IOException("archive contains no files");
            }
            Files.move(staging, destinationDirectory);
            restored = true;
        } finally {
            if (!restored) {
                deleteTree(staging);
            }
        }
    }

    private static int extract(byte[] archive, Path staging) throws IOException {
        int files = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path destination = staging.resolve(entry.getName()).normalize();
                if (!destination.startsWith(staging)) {
                    throw new IllegalArgumentException("archive entry escapes output directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(zip, destination);
                    files++;
                }
                zip.closeEntry();
            }
        }
        return files;
    }

    private static void deleteTree(Path directory) {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // A failed restore remains a cold build. Best-effort cleanup must not change that.
        }
    }
}
