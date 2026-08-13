package cn.trialfinder.sim.template;

import cn.trialfinder.sim.math.BlockPos;
import cn.trialfinder.sim.math.BoundingBox;
import cn.trialfinder.sim.math.Rotation;
import cn.trialfinder.sim.math.Vec3i;
import cn.trialfinder.sim.nbt.NbtIo;
import cn.trialfinder.sim.nbt.NbtTag;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 复刻 {@code StructureTemplate}（26.2 语义）—— 枚举 jigsaw 连接块与 trial_spawner 方块所需的子集。
 * 模板 NBT 由 {@link NbtIo} 解析；实体数据忽略（刷怪笼是方块，不是实体）。
 */
public class StructureTemplate {
    private Vec3i size = Vec3i.ZERO;
    private final List<Palette> palettes = new ArrayList<>();
    private final EnumMap<Rotation, List<JigsawBlockInfo>> rotatedJigsaws = new EnumMap<>(Rotation.class);
    private final EnumMap<Rotation, BoundingBox> rotatedBounds = new EnumMap<>(Rotation.class);
    private final EnumMap<Rotation, List<StructureBlockInfo>> rotatedSpawners = new EnumMap<>(Rotation.class);

    public Vec3i getSize() {
        return this.size;
    }

    /** 返回按 {@code rotation} 旋转并平移到 {@code pos} 的 jigsaw 方块列表。 */
    public List<JigsawBlockInfo> getJigsaws(BlockPos pos, Rotation rotation) {
        if (this.palettes.isEmpty()) {
            return new ArrayList<>();
        }
        List<JigsawBlockInfo> base = rotatedJigsaws(rotation);
        boolean zero = pos.getX() == 0 && pos.getY() == 0 && pos.getZ() == 0;
        if (zero) {
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
        if (this.palettes.isEmpty()) {
            return List.of();
        }
        Palette palette = this.palettes.get(0);
        if (!palette.contains(blockName)) {
            return List.of();
        }
        List<StructureBlockInfo> result = new ArrayList<>();
        for (StructureBlockInfo info : palette.blocks(blockName)) {
            BlockPos transformed = calculateRelativePosition(settings, info.pos()).offset(pos);
            result.add(new StructureBlockInfo(transformed, info.state().rotate(settings.getRotation()), info.nbt()));
        }
        return result;
    }

    /** 返回按 {@code rotation} 旋转、原点在 ZERO 的 trial_spawner 方块（调用方再平移到 piece 位置）。 */
    public List<StructureBlockInfo> getSpawnerBlocks(Rotation rotation) {
        if (this.palettes.isEmpty()) {
            return List.of();
        }
        return this.rotatedSpawners.computeIfAbsent(rotation, r -> {
            StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(r);
            List<StructureBlockInfo> base = this.palettes.get(0).blocks("minecraft:trial_spawner");
            if (base.isEmpty()) {
                return List.of();
            }
            List<StructureBlockInfo> list = new ArrayList<>(base.size());
            for (StructureBlockInfo info : base) {
                BlockPos transformed = calculateRelativePosition(settings, info.pos());
                list.add(new StructureBlockInfo(transformed, info.state().rotate(r), info.nbt()));
            }
            return List.copyOf(list);
        });
    }

    /** 该模板第一 palette 中指定状态名的方块数（O(1)）。 */
    public int countBlocks(String blockName) {
        if (this.palettes.isEmpty()) {
            return 0;
        }
        return this.palettes.get(0).count(blockName);
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
        return switch (rotation) {
            case CLOCKWISE_90 -> new BlockPos(pivotX + pivotZ - z, y, pivotZ - pivotX + x);
            case CLOCKWISE_180 -> new BlockPos(pivotX + pivotX - x, y, pivotZ + pivotZ - z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(pivotX - pivotZ + z, y, pivotX + pivotZ - x);
            default -> mirrored ? new BlockPos(x, y, z) : pos;
        };
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

    protected static BoundingBox getBoundingBox(
            BlockPos pos, Rotation rotation, BlockPos pivot, Mirror mirror, Vec3i size) {
        Vec3i size2 = size.offset(-1, -1, -1);
        BlockPos p1 = transform(BlockPos.ZERO, mirror, rotation, pivot);
        BlockPos p2 = transform(BlockPos.ZERO.offset(size2), mirror, rotation, pivot);
        return BoundingBox.fromCorners(p1, p2).moved(pos.getX(), pos.getY(), pos.getZ());
    }

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
        List<StructureBlockInfo> withNbt = new ArrayList<>();
        List<StructureBlockInfo> other = new ArrayList<>();
        for (NbtTag.Compound blockTag : blocksList.asCompoundList()) {
            NbtTag.List posList = blockTag.getList("pos");
            BlockPos pos = new BlockPos(posList.getInt(0), posList.getInt(1), posList.getInt(2));
            BlockState state = states.get(Math.min(Math.max(blockTag.getInt("state"), 0), states.size() - 1));
            NbtTag.Compound nbt = blockTag.contains("nbt") ? blockTag.getCompound("nbt") : null;
            StructureBlockInfo info = new StructureBlockInfo(pos, state, nbt);
            addToLists(info, list, withNbt, other);
        }
        List<StructureBlockInfo> merged = buildInfoList(list, withNbt, other);
        this.palettes.add(new Palette(merged));
    }

    private static void addToLists(StructureBlockInfo info,
                                   List<StructureBlockInfo> full,
                                   List<StructureBlockInfo> withNbt,
                                   List<StructureBlockInfo> other) {
        if (info.nbt() != null) {
            withNbt.add(info);
        } else if (info.state().isJigsaw()) {
            full.add(info);
        } else {
            other.add(info);
        }
    }

    private static List<StructureBlockInfo> buildInfoList(
            List<StructureBlockInfo> a, List<StructureBlockInfo> b, List<StructureBlockInfo> c) {
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

    /** vanilla Mirror，jigsaw piece 恒为 NONE。 */
    public enum Mirror {
        NONE,
        LEFT_RIGHT,
        FRONT_BACK
    }

    static final class Palette {
        private final List<StructureBlockInfo> blocks;
        private final List<JigsawBlockInfo> jigsaws;
        private final Map<String, List<StructureBlockInfo>> byName;

        Palette(List<StructureBlockInfo> blocks) {
            this.blocks = List.copyOf(blocks);
            List<JigsawBlockInfo> jigsaws = new ArrayList<>();
            Map<String, List<StructureBlockInfo>> byName = new HashMap<>();
            for (StructureBlockInfo block : blocks) {
                if (block.state().isJigsaw()) {
                    jigsaws.add(JigsawBlockInfo.of(block));
                }
                byName.computeIfAbsent(block.state().name(), ignored -> new ArrayList<>()).add(block);
            }
            this.jigsaws = List.copyOf(jigsaws);
            byName.replaceAll((k, v) -> List.copyOf(v));
            this.byName = Map.copyOf(byName);
        }

        List<JigsawBlockInfo> jigsaws() {
            return this.jigsaws;
        }

        boolean contains(String blockName) {
            return this.byName.containsKey(blockName);
        }

        List<StructureBlockInfo> blocks(String blockName) {
            return this.byName.getOrDefault(blockName, List.of());
        }

        int count(String blockName) {
            return this.byName.getOrDefault(blockName, List.of()).size();
        }
    }
}
