package cn.trialfinder.cuda;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Assembles PTX into a cubin for a specific compute capability using the standalone {@code ptxas}
 * binary (no MSVC host compiler required). This is needed because NVRTC from a newer toolkit may
 * emit a PTX ISA newer than the installed driver supports; a cubin is arch-specific SASS and loads
 * without a PTX-version check.
 *
 * <p>The resulting cubin is cached on disk keyed by a SHA-256 of (PTX text + target), so repeated
 * runs skip the ~0.4 s ptxas process entirely.
 */
public final class PtxCompiler {
    private PtxCompiler() {
    }

    private static final Path CACHE_DIR = Path.of(
            System.getProperty("trialfinder.cubin.cache",
                    System.getProperty("java.io.tmpdir", "/tmp") + "/trialfinder-cubin"));

    /** Assembles {@code ptx} for {@code sm_<major><minor>} and returns the cubin bytes (cached). */
    public static byte[] ptxToCubin(String ptx, int major, int minor) {
        String target = "sm_" + major + minor;
        String key = sha256(ptx + "|" + target);
        try {
            Files.createDirectories(CACHE_DIR);
            Path cached = CACHE_DIR.resolve("kernels-" + key + ".cubin");
            if (Files.isRegularFile(cached)) {
                return Files.readAllBytes(cached);
            }
        } catch (IOException e) {
            // cache unavailable — fall through to fresh compile
        }

        String ptxas = findPtxas();
        Path ptxFile = null;
        Path cubinFile = null;
        try {
            ptxFile = Files.createTempFile("trial_finder_", ".ptx");
            cubinFile = Files.createTempFile("trial_finder_", ".cubin");
            Files.writeString(ptxFile, ptx, StandardCharsets.UTF_8);

            ProcessBuilder pb = new ProcessBuilder(ptxas, "--gpu-name=" + target,
                    "-o", cubinFile.toString(), ptxFile.toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IllegalStateException("ptxas failed (exit " + exit + ") for " + target + ":\n" + output);
            }
            byte[] cubin = Files.readAllBytes(cubinFile);
            writeCache(key, cubin);
            return cubin;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ptxas invocation failed: " + e.getMessage(), e);
        } finally {
            deleteQuietly(ptxFile);
            deleteQuietly(cubinFile);
        }
    }

    private static void writeCache(String key, byte[] cubin) {
        try {
            Files.createDirectories(CACHE_DIR);
            Files.write(CACHE_DIR.resolve("kernels-" + key + ".cubin"), cubin);
        } catch (IOException ignored) {
            // best-effort cache write
        }
    }

    private static String sha256(String text) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    private static String findPtxas() {
        List<String> candidates = new ArrayList<>();
        String cudaPath = System.getenv("CUDA_PATH");
        if (cudaPath != null && !cudaPath.isBlank()) {
            candidates.add(cudaPath + "\\bin\\ptxas.exe");
            candidates.add(cudaPath + "\\bin\\x64\\ptxas.exe");
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(";")) {
                candidates.add(dir + "\\ptxas.exe");
            }
        }
        for (String candidate : candidates) {
            if (candidate != null && Files.isRegularFile(Path.of(candidate))) {
                return candidate;
            }
        }
        throw new IllegalStateException("ptxas.exe not found. Add the CUDA toolkit bin directory to PATH.");
    }

    private static void deleteQuietly(Path file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // best-effort
            }
        }
    }
}
