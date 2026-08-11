package cn.trialfinder.sim.nbt;

/**
 * Minimal NBT tag types used by the trial-chamber template loader.
 * The API intentionally mirrors Mojang's net.minecraft.nbt so ported code reads naturally.
 */
public interface NbtTag {

    record Compound(java.util.Map<String, NbtTag> entries) implements NbtTag {

        public NbtTag get(String key) {
            return entries.get(key);
        }

        public String getString(String key) {
            NbtTag tag = entries.get(key);
            return tag instanceof Str s ? s.value() : "";
        }

        public int getInt(String key) {
            NbtTag tag = entries.get(key);
            if (tag instanceof Int i) {
                return i.value();
            }
            if (tag instanceof Short s) {
                return s.value();
            }
            if (tag instanceof Byte b) {
                return b.value();
            }
            if (tag instanceof Long l) {
                return (int) l.value();
            }
            return 0;
        }

        public long getLong(String key) {
            NbtTag tag = entries.get(key);
            if (tag instanceof Long l) {
                return l.value();
            }
            return getInt(key);
        }

        public byte getByte(String key) {
            NbtTag tag = entries.get(key);
            if (tag instanceof Byte b) {
                return b.value();
            }
            return (byte) getInt(key);
        }

        public boolean getBoolean(String key) {
            return getByte(key) != 0;
        }

        public List getList(String key) {
            NbtTag tag = entries.get(key);
            return tag instanceof List list ? list : new List(java.util.List.of(), NbtTag.Type.END);
        }

        public Compound getCompound(String key) {
            NbtTag tag = entries.get(key);
            return tag instanceof Compound c ? c : new Compound(java.util.Map.of());
        }

        public boolean contains(String key) {
            return entries.containsKey(key);
        }
    }

    record List(java.util.List<NbtTag> elements, NbtTag.Type elementType) implements NbtTag {
        public int size() {
            return elements.size();
        }

        public Compound getCompound(int index) {
            return (Compound) elements.get(index);
        }

        public int getInt(int index) {
            return ((Int) elements.get(index)).value();
        }

        public long getLong(int index) {
            NbtTag tag = elements.get(index);
            return tag instanceof Long l ? l.value() : ((Int) tag).value();
        }

        public double getDouble(int index) {
            NbtTag tag = elements.get(index);
            return tag instanceof Double d ? d.value() : ((Float) tag).value();
        }

        public java.util.List<Compound> asCompoundList() {
            return elements.stream().map(tag -> (Compound) tag).toList();
        }
    }

    record Str(String value) implements NbtTag {
    }

    record Int(int value) implements NbtTag {
    }

    record Short(short value) implements NbtTag {
    }

    record Byte(byte value) implements NbtTag {
    }

    record Long(long value) implements NbtTag {
    }

    record Float(float value) implements NbtTag {
    }

    record Double(double value) implements NbtTag {
    }

    record ByteArray(byte[] value) implements NbtTag {
    }

    record IntArray(int[] value) implements NbtTag {
    }

    record LongArray(long[] value) implements NbtTag {
    }

    enum Type {
        END(0),
        BYTE(1),
        SHORT(2),
        INT(3),
        LONG(4),
        FLOAT(5),
        DOUBLE(6),
        BYTE_ARRAY(7),
        STRING(8),
        LIST(9),
        COMPOUND(10),
        INT_ARRAY(11),
        LONG_ARRAY(12);

        final int id;

        Type(int id) {
            this.id = id;
        }

        public static Type byId(int id) {
            for (Type type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown NBT type id: " + id);
        }
    }
}
