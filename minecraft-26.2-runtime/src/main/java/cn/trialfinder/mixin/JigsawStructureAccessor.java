package cn.trialfinder.mixin;

import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

@Mixin(JigsawStructure.class)
public interface JigsawStructureAccessor {
    @Accessor("startPool")
    Holder<StructureTemplatePool> trialFinder$getStartPool();

    @Accessor("startJigsawName")
    Optional<Identifier> trialFinder$getStartJigsawName();

    @Accessor("maxDepth")
    int trialFinder$getSize();

    @Accessor("startHeight")
    HeightProvider trialFinder$getStartHeight();

    @Accessor("useExpansionHack")
    boolean trialFinder$getUseExpansionHack();

    @Accessor("projectStartToHeightmap")
    Optional<Heightmap.Types> trialFinder$getProjectStartToHeightmap();

    @Accessor("maxDistanceFromCenter")
    JigsawStructure.MaxDistance trialFinder$getMaxDistanceFromCenter();

    @Accessor("dimensionPadding")
    DimensionPadding trialFinder$getDimensionPadding();
}
