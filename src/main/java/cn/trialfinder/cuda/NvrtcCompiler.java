package cn.trialfinder.cuda;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * Compiles CUDA-C source to PTX at runtime via NVRTC.
 */
public final class NvrtcCompiler {
    private static final int NVRTC_SUCCESS = 0;

    private NvrtcCompiler() {
    }

    /** Compiles {@code source} for the given gpu architecture (e.g. "compute_86") and returns PTX text. */
    public static String compile(String source, String arch) {
        Nvrtc nvrtc = Nvrtc.INSTANCE;

        PointerByReference program = new PointerByReference();
        int err = nvrtc.nvrtcCreateProgram(program, source, "trial_finder_kernels.cu", 0, null, null);
        if (err != NVRTC_SUCCESS) {
            throw new IllegalStateException("nvrtcCreateProgram failed: " + nvrtc.nvrtcGetErrorString(err));
        }
        Pointer handle = program.getValue();

        String[] options = {"--gpu-architecture=" + arch};
        err = nvrtc.nvrtcCompileProgram(handle, options.length, options);
        if (err != NVRTC_SUCCESS) {
            String log = getLog(nvrtc, handle);
            throw new IllegalStateException("nvrtcCompileProgram failed: " + nvrtc.nvrtcGetErrorString(err)
                    + "\n" + log);
        }

        LongByReference ptxSize = new LongByReference();
        err = nvrtc.nvrtcGetPTXSize(handle, ptxSize);
        if (err != NVRTC_SUCCESS) {
            throw new IllegalStateException("nvrtcGetPTXSize failed: " + nvrtc.nvrtcGetErrorString(err));
        }
        Memory ptx = new Memory(ptxSize.getValue());
        err = nvrtc.nvrtcGetPTX(handle, ptx);
        if (err != NVRTC_SUCCESS) {
            throw new IllegalStateException("nvrtcGetPTX failed: " + nvrtc.nvrtcGetErrorString(err));
        }
        return ptx.getString(0);
    }

    private static String getLog(Nvrtc nvrtc, Pointer handle) {
        try {
            LongByReference logSize = new LongByReference();
            nvrtc.nvrtcGetProgramLogSize(handle, logSize);
            Memory log = new Memory(logSize.getValue() + 1);
            nvrtc.nvrtcGetProgramLog(handle, log);
            return log.getString(0);
        } catch (Throwable t) {
            return "(log unavailable: " + t.getMessage() + ")";
        }
    }
}
