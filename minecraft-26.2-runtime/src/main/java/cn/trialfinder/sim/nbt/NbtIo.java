package cn.trialfinder.sim.nbt;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * 模板文件（Minecraft .nbt，gzip 压缩）的最小只读 NBT 解析器。
 * 支持模板需要的 compound/list/标量类型。
 */
public final class NbtIo {
    private NbtIo() {
    }

    public static NbtTag.Compound readCompressed(byte[] bytes) throws IOException {
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes))))) {
            return readCompound(input);
        }
    }

    public static NbtTag.Compound readCompressed(InputStream stream) throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(new GZIPInputStream(stream)))) {
            return readCompound(input);
        }
    }

    public static NbtTag.Compound read(InputStream stream) throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(stream))) {
            return readCompound(input);
        }
    }

    private static NbtTag.Compound readCompound(DataInputStream input) throws IOException {
        byte rootType = input.readByte();
        if (rootType != NbtTag.Type.COMPOUND.id) {
            throw new IOException("根 NBT 标签必须是 COMPOUND，实际是 " + rootType);
        }
        readString(input);
        return readCompoundPayload(input);
    }

    private static NbtTag.Compound readCompoundPayload(DataInputStream input) throws IOException {
        Map<String, NbtTag> entries = new LinkedHashMap<>();
        byte type;
        while ((type = input.readByte()) != NbtTag.Type.END.id) {
            String name = readString(input);
            NbtTag tag = readTagPayload(input, NbtTag.Type.byId(type));
            entries.put(name, tag);
        }
        return new NbtTag.Compound(entries);
    }

    private static NbtTag readTagPayload(DataInputStream input, NbtTag.Type type) throws IOException {
        switch (type) {
            case BYTE -> {
                return new NbtTag.Byte(input.readByte());
            }
            case SHORT -> {
                return new NbtTag.Short(input.readShort());
            }
            case INT -> {
                return new NbtTag.Int(input.readInt());
            }
            case LONG -> {
                return new NbtTag.Long(input.readLong());
            }
            case FLOAT -> {
                return new NbtTag.Float(input.readFloat());
            }
            case DOUBLE -> {
                return new NbtTag.Double(input.readDouble());
            }
            case BYTE_ARRAY -> {
                int length = input.readInt();
                if (length < 0) {
                    throw new IOException("负的字节数组长度: " + length);
                }
                byte[] bytes = new byte[length];
                input.readFully(bytes);
                return new NbtTag.ByteArray(bytes);
            }
            case STRING -> {
                return new NbtTag.Str(readString(input));
            }
            case LIST -> {
                byte elementTypeId = input.readByte();
                NbtTag.Type elementType = NbtTag.Type.byId(elementTypeId);
                int length = input.readInt();
                if (length < 0) {
                    throw new IOException("负的列表长度: " + length);
                }
                List<NbtTag> elements = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    elements.add(readTagPayload(input, elementType));
                }
                return new NbtTag.List(elements, elementType);
            }
            case COMPOUND -> {
                return readCompoundPayload(input);
            }
            case INT_ARRAY -> {
                int length = input.readInt();
                if (length < 0) {
                    throw new IOException("负的整数数组长度: " + length);
                }
                int[] values = new int[length];
                for (int i = 0; i < length; i++) {
                    values[i] = input.readInt();
                }
                return new NbtTag.IntArray(values);
            }
            case LONG_ARRAY -> {
                int length = input.readInt();
                if (length < 0) {
                    throw new IOException("负的长整数数组长度: " + length);
                }
                long[] values = new long[length];
                for (int i = 0; i < length; i++) {
                    values[i] = input.readLong();
                }
                return new NbtTag.LongArray(values);
            }
            default -> throw new IOException("不支持的 NBT 类型: " + type);
        }
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readUnsignedShort();
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
