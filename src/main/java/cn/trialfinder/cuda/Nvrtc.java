package cn.trialfinder.cuda;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal JNA binding to the CUDA NVRTC runtime API (nvrtc64_*_0.dll). NVRTC compiles CUDA-C
 * source to PTX at runtime without needing {@code nvcc} or an MSVC host compiler.
 *
 * <p>JCuda's own {@code JNvrtc} JNI is built against a specific CUDA minor version and fails to
 * load against the installed CUDA 13.3 runtime; calling the C API directly via JNA is
 * version-agnostic.
 */
public interface Nvrtc extends Library {

    Nvrtc INSTANCE = NvrtcLoader.load();

    // nvrtcResult nvrtcCreateProgram(nvrtcProgram *prog, const char* src, const char* name,
    //                                int numHeaders, const char** headers, const char** includeNames);
    int nvrtcCreateProgram(PointerByReference prog, String src, String name,
                           int numHeaders, String[] headers, String[] includeNames);

    // nvrtcResult nvrtcCompileProgram(nvrtcProgram prog, int numOptions, const char** options);
    int nvrtcCompileProgram(Pointer prog, int numOptions, String[] options);

    // nvrtcResult nvrtcGetPTXSize(nvrtcProgram prog, size_t* ptxSizeRet);
    int nvrtcGetPTXSize(Pointer prog, com.sun.jna.ptr.LongByReference ptxSize);

    // nvrtcResult nvrtcGetPTX(nvrtcProgram prog, char* ptx);
    int nvrtcGetPTX(Pointer prog, Memory ptx);

    // nvrtcResult nvrtcGetProgramLogSize(nvrtcProgram prog, size_t* logSizeRet);
    int nvrtcGetProgramLogSize(Pointer prog, com.sun.jna.ptr.LongByReference logSize);

    // nvrtcResult nvrtcGetProgramLog(nvrtcProgram prog, char* log);
    int nvrtcGetProgramLog(Pointer prog, Memory log);

    // const char* nvrtcGetErrorString(nvrtcResult result);
    String nvrtcGetErrorString(int result);

    /** Resolves the NVRTC library by name/path, searching the CUDA toolkit and PATH. */
    final class NvrtcLoader {
        private NvrtcLoader() {
        }

        static Nvrtc load() {
            List<String> candidates = new ArrayList<>();
            candidates.add("nvrtc64_130_0");
            candidates.add("nvrtc64_120_0");
            candidates.add("nvrtc");

            String cudaPath = System.getenv("CUDA_PATH");
            if (cudaPath != null && !cudaPath.isBlank()) {
                candidates.add(cudaPath + "\\bin\\x64\\nvrtc64_130_0");
                candidates.add(cudaPath + "\\bin\\x64\\nvrtc");
                candidates.add(cudaPath + "\\bin\\nvrtc64_130_0");
                candidates.add(cudaPath + "\\bin\\nvrtc");
            }
            String path = System.getenv("PATH");
            if (path != null) {
                for (String dir : path.split(";")) {
                    try {
                        Files.list(Path.of(dir))
                                .filter(Files::isRegularFile)
                                .filter(p -> p.getFileName().toString().matches("nvrtc64_\\d+_\\d+\\.dll|nvrtc\\.dll"))
                                .forEach(p -> {
                                    String name = p.getFileName().toString();
                                    candidates.add(name.substring(0, name.length() - 4));
                                    candidates.add(dir + "\\" + name.substring(0, name.length() - 4));
                                });
                    } catch (Exception ignored) {
                        // not a valid dir
                    }
                }
            }
            for (String candidate : candidates) {
                try {
                    return Native.load(candidate, Nvrtc.class);
                } catch (Throwable ignored) {
                    // try next candidate
                }
            }
            throw new IllegalStateException("NVRTC library not found (nvrtc64_*_0.dll). "
                    + "Install the CUDA toolkit or add its bin/x64 directory to PATH.");
        }
    }
}
