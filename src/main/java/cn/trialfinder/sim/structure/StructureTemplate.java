package cn.trialfinder.sim.structure;

import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.math.Vec3i;
import cn.trialfinder.sim.nbt.NbtIo;
import cn.trialfinder.sim.nbt.NbtTag;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Port of net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate (1.21.11)
 * — the subset needed to enumerate jigsaw connection blocks and trial-spawner blocks. Template
 * NBT is parsed by {@link NbtIo}; entity data is ignored (spawners are blocks, not entities).
 */
public class StructureTemplate {
    private Vec3i size = Vec3i.ZERO;
    private final List<Palette> palettes = new ArrayList<>();
    /** Per-rotation precomputed jigsaw blocks (rotated, ZERO-based, state rotated) — cache key. */
    private final java.util.EnumMap<Rotation, List<JigsawBlockInfo>> rotatedJigsaws =
            new java.util.EnumMap<>(Rotation.class);
    /** Per-rotation bounding box at ZERO origin (Mirror.NONE, ZERO pivot); moved to pos on demand. */
    private final java.util.EnumMap<Rotation, BoundingBox> rotatedBounds =
            new java.util.EnumMap<>(Rotation.class);

    public Vec3i getSize() {
        return this.size;
    }

    /**
     * Returns the jigsaw blocks rotated by {@code rotation} and offset by {@code pos}. The rotated
     * transform (coordinate + block-state rotation) is independent of {@code pos}, so it is computed
     * once per rotation and cached; a {@code ZERO} offset shares the cached elements directly. The
     * returned list is a fresh {@link ArrayList} so callers (e.g. {@code Util.shuffle}) may mutate it.
     */
    public List<JigsawBlockInfo> getJigsaws(BlockPos pos, Rotation rotation) {
        if (this.palettes.isEmpty()) {
            return new ArrayList<>();
        }
        List<JigsawBlockInfo> base = rotatedJigsaws(rotation);
        boolean zero = pos.getX() == 0 && pos.getY() == 0 && pos.getZ() == 0;
        if (zero) {
            // Share cached (immutable) elements; the list itself must be fresh for shuffling.
            return new ArrayList<>(base);
        }
        List<JigsawBlockInfo> result = new ArrayList<>(base.size());
        for (JigsawBlockInfo jigsaw : base) {
            StructureBlockInfo info = jigsaw.info();
            BlockPos offset = info.pos().offset(pos);
            StructureBlockInfo newInfo = new StructureBlockInfo(offset, info.state(), info.nbt());
            result.add(jigsaw.withInfo(newInfo));
        }
        return result;
    }

    /** Lazily computes the rotation-transformed jigsaw list for one rotation (ZERO-based). */
    private List<JigsawBlockInfo> rotatedJigsaws(Rotation rotation) {
        return this.rotatedJigsaws.computeIfAbsent(rotation, r -> {
            StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(r);
            List<JigsawBlockInfo> base = this.palettes.get(0).jigsaws();
            List<JigsawBlockInfo> list = new ArrayList<>(base.size());
            for (JigsawBlockInfo jigsaw : base) {
                StructureBlockInfo info = jigsaw.info();
                BlockPos transformed = calculateRelativePosition(settings, info.pos());
                StructureBlockInfo newInfo = new StructureBlockInfo(
                        transformed, info.state().rotate(settings.getRotation()), info.nbt());
                list.add(jigsaw.withInfo(newInfo));
            }
            return List.copyOf(list);
        });
    }

    public List<StructureBlockInfo> filterBlocks(BlockPos pos, StructurePlaceSettings settings, String blockName) {
        List<StructureBlockInfo> result = new ArrayList<>();
        if (this.palettes.isEmpty()) {
            return result;
        }
        for (StructureBlockInfo info : this.palettes.get(0).blocks(blockName)) {
            BlockPos transformed = calculateRelativePosition(settings, info.pos()).offset(pos);
            result.add(new StructureBlockInfo(transformed, info.state().rotate(settings.getRotation()), info.nbt()));
        }
        return result;
    }

    public Vec3i getSize(Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> new Vec3i(this.size.getZ(), this.size.getY(), this.size.getX());
            default -> this.size;
        };
    }

    public static BlockPos calculateRelativePosition(StructurePlaceSettings settings, BlockPos pos) {
        return transform(pos, Mirror.NONE, settings.getRotation(), settings.getRotationPivot());
    }

    public static BlockPos transform(BlockPos pos, Mirror mirror, Rotation rotation, BlockPos pivot) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        boolean mirrored = true;
        switch (mirror) {
            case FRONT_BACK -> z = -z;
            case LEFT_RIGHT -> x = -x;
            default -> mirrored = false;
        }
        int pivotX = pivot.getX();
        int pivotZ = pivot.getZ();
        // Formulas verified against the compiled 1.21.11 StructureTemplate.transform.
        // Switch-map ordering is CCW90→case1, CW90→case2, CW180→case3.
        return switch (rotation) {
            case CLOCKWISE_90 -> new BlockPos(pivotX + pivotZ - z, y, pivotZ - pivotX + x);
            case CLOCKWISE_180 -> new BlockPos(pivotX + pivotX - x, y, pivotZ + pivotZ - z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(pivotX - pivotZ + z, y, pivotX + pivotZ - x);
            default -> mirrored ? new BlockPos(x, y, z) : pos;
        };
    }

    public BlockPos getZeroPositionWithTransform(BlockPos pos, Mirror mirror, Rotation rotation, int sizeX, int sizeZ) {
        int i = sizeX - 1;
        int j = sizeZ - 1;
        int k = mirror == Mirror.FRONT_BACK ? i : 0;
        int l = mirror == Mirror.LEFT_RIGHT ? j : 0;
        BlockPos result = pos;
        // Switch-map ordering verified against compiled 1.21.11: CCW90→case1, CW90→case2, CW180→case3.
        switch (rotation) {
            case CLOCKWISE_90 -> result = pos.offset(j - l, 0, k);
            case CLOCKWISE_180 -> result = pos.offset(i - k, 0, j - l);
            case COUNTERCLOCKWISE_90 -> result = pos.offset(l, 0, i - k);
            default -> result = pos.offset(k, 0, l);
        }
        return result;
    }

    public BoundingBox getBoundingBox(StructurePlaceSettings settings, BlockPos pos) {
        return this.getBoundingBox(pos, settings.getRotation(), settings.getRotationPivot(), Mirror.NONE);
    }

    public BoundingBox getBoundingBox(BlockPos pos, Rotation rotation, BlockPos pivot, Mirror mirror) {
        if (pivot == BlockPos.ZERO && mirror == Mirror.NONE) {
            return this.rotatedBounds.computeIfAbsent(rotation,
                    r -> getBoundingBox(BlockPos.ZERO, r, BlockPos.ZERO, Mirror.NONE, this.size))
                    .moved(pos.getX(), pos.getY(), pos.getZ());
        }
        return getBoundingBox(pos, rotation, pivot, mirror, this.size);
    }

    protected static BoundingBox getBoundingBox(BlockPos pos, Rotation rotation, BlockPos pivot, Mirror mirror, Vec3i size) {
        Vec3i size2 = size.offset(-1, -1, -1);
        BlockPos p1 = transform(BlockPos.ZERO, mirror, rotation, pivot);
        BlockPos p2 = transform(BlockPos.ZERO.offset(size2), mirror, rotation, pivot);
        return BoundingBox.fromCorners(p1, p2).moved(pos.getX(), pos.getY(), pos.getZ());
    }

    /** Parses a template from NBT. */
    public void load(NbtTag.Compound tag) {
        this.palettes.clear();
        NbtTag.List sizeList = tag.getList("size");
        this.size = new Vec3i(sizeList.getInt(0), sizeList.getInt(1), sizeList.getInt(2));
        NbtTag.List blocksList = tag.getList("blocks");
        NbtTag.List paletteList = tag.getList("palette");
        this.loadPalette(paletteList, blocksList);
    }

    private void loadPalette(NbtTag.List paletteList, NbtTag.List blocksList) {
        List<BlockState> states = new ArrayList<>(paletteList.size());
        for (int i = 0; i < paletteList.size(); i++) {
            NbtTag.Compound stateTag = paletteList.getCompound(i);
            String name = stateTag.getString("Name");
            String orientation = null;
            if (stateTag.contains("Properties")) {
                NbtTag.Compound properties = stateTag.getCompound("Properties");
                if (properties.contains("orientation")) {
                    orientation = properties.getString("orientation");
                }
            }
            states.add(new BlockState(name, orientation));
        }

        List<StructureBlockInfo> list = new ArrayList<>();
        List<StructureBlockInfo> list2 = new ArrayList<>();
        List<StructureBlockInfo> list3 = new ArrayList<>();
        for (NbtTag.Compound blockTag : blocksList.asCompoundList()) {
            NbtTag.List posList = blockTag.getList("pos");
            BlockPos pos = new BlockPos(posList.getInt(0), posList.getInt(1), posList.getInt(2));
            BlockState state = states.get(Math.min(Math.max(blockTag.getInt("state"), 0), states.size() - 1));
            NbtTag.Compound nbt = blockTag.contains("nbt") ? blockTag.getCompound("nbt") : null;
            StructureBlockInfo info = new StructureBlockInfo(pos, state, nbt);
            addToLists(info, list, list2, list3);
        }
        List<StructureBlockInfo> merged = buildInfoList(list, list2, list3);
        this.palettes.add(new Palette(merged));
    }

    private static void addToLists(StructureBlockInfo info, List<StructureBlockInfo> full, List<StructureBlockInfo> withNbt, List<StructureBlockInfo> other) {
        if (info.nbt() != null) {
            withNbt.add(info);
        } else if (info.state().isJigsaw()) {
            full.add(info);
        } else {
            other.add(info);
        }
    }

    private static List<StructureBlockInfo> buildInfoList(List<StructureBlockInfo> a, List<StructureBlockInfo> b, List<StructureBlockInfo> c) {
        Comparator<StructureBlockInfo> comparator = Comparator
                .comparingInt((StructureBlockInfo info) -> info.pos().getY())
                .thenComparingInt(info -> info.pos().getX())
                .thenComparingInt(info -> info.pos().getZ());
        a.sort(comparator);
        b.sort(comparator);
        c.sort(comparator);
        List<StructureBlockInfo> result = new ArrayList<>(a.size() + b.size() + c.size());
        result.addAll(a);
        result.addAll(c);
        result.addAll(b);
        return result;
    }

    /** Loads and parses a compressed .nbt template from an input stream. */
    public static StructureTemplate loadFrom(InputStream stream) throws IOException {
        StructureTemplate template = new StructureTemplate();
        template.load(NbtIo.readCompressed(stream));
        return template;
    }

    public static StructureTemplate loadFrom(Path path) throws IOException {
        StructureTemplate template = new StructureTemplate();
        try (InputStream stream = Files.newInputStream(path)) {
            template.load(NbtIo.readCompressed(stream));
        }
        return template;
    }

    /** Vanilla Mirror, always NONE for jigsaw pieces. */
    public enum Mirror {
        NONE,
        LEFT_RIGHT,
        FRONT_BACK
    }

    static final class Palette {
        private final List<StructureBlockInfo> blocks;
        private final List<JigsawBlockInfo> jigsaws;

        Palette(List<StructureBlockInfo> blocks) {
            this.blocks = List.copyOf(blocks);
            List<JigsawBlockInfo> jigsaws = new ArrayList<>();
            for (StructureBlockInfo block : blocks) {
                if (block.state().isJigsaw()) {
                    jigsaws.add(JigsawBlockInfo.of(block));
                }
            }
            this.jigsaws = List.copyOf(jigsaws);
        }

        List<JigsawBlockInfo> jigsaws() {
            return this.jigsaws;
        }

        List<StructureBlockInfo> blocks() {
            return this.blocks;
        }

        List<StructureBlockInfo> blocks(String blockName) {
            List<StructureBlockInfo> result = new ArrayList<>();
            for (StructureBlockInfo block : this.blocks) {
                if (block.state().name().equals(blockName)) {
                    result.add(block);
                }
            }
            return result;
        }
    }
}
