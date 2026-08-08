package io.github.baokhang83.blastradius.cachewarmer.warmer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Restores a ZIP archive through staging without allowing path traversal. */
final class ArchiveRestorer {

    private ArchiveRestorer() {
    }

    static void restore(byte[] archive, Path destinationDirectory) throws IOException {
        restore(archive, destinationDirectory, entryName -> true);
    }

    static void restoreClassFiles(byte[] archive, Path destinationDirectory) throws IOException {
        restore(archive, destinationDirectory, entryName -> entryName.endsWith(".class"));
    }

    private static void restore(byte[] archive, Path destinationDirectory, Predicate<String> includeEntry)
            throws IOException {
        Path parent = destinationDirectory.getParent();
        if (parent == null) {
            throw new IOException("destination has no parent: " + destinationDirectory);
        }
        Files.createDirectories(parent);
        Path staging = Files.createTempDirectory(parent, ".cache-warmer-");
        try {
            int files = extract(archive, staging, includeEntry);
            if (files == 0) {
                throw new IOException("archive contains no files");
            }
            if (Files.exists(destinationDirectory)) {
                merge(staging, destinationDirectory);
            } else {
                Files.move(staging, destinationDirectory);
            }
        } finally {
            if (Files.exists(staging)) {
                deleteTree(staging);
            }
        }
    }

    private static int extract(byte[] archive, Path staging, Predicate<String> includeEntry) throws IOException {
        int files = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path destination = staging.resolve(entry.getName()).normalize();
                if (!destination.startsWith(staging)) {
                    throw new IllegalArgumentException("archive entry escapes output directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    if (includeEntry.test(entry.getName())) {
                        Files.createDirectories(destination);
                    }
                } else if (includeEntry.test(entry.getName())) {
                    Files.createDirectories(destination.getParent());
                    Files.copy(zip, destination);
                    files++;
                }
                zip.closeEntry();
            }
        }
        return files;
    }

    private static void merge(Path staging, Path destinationDirectory) throws IOException {
        try (var paths = Files.walk(staging)) {
            for (Path source : paths.filter(Files::isRegularFile).toList()) {
                Path destination = destinationDirectory.resolve(staging.relativize(source));
                Files.createDirectories(destination.getParent());
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
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
