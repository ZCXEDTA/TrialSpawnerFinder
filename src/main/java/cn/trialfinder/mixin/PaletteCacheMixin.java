package cn.trialfinder.mixin;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.21.11 Mojang-mapped port of the original 1.21.1 PalettedBlockInfoListMixin. The trial-chamber
 * finder generates many structures in parallel, and {@code StructureTemplate$Palette.blocks(Block)}
 * lazily populates its {@code cache} map via {@code computeIfAbsent}. A plain HashMap is not
 * thread-safe for concurrent {@code computeIfAbsent} on different keys, so we swap it for a
 * ConcurrentHashMap at construction.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate$Palette")
public abstract class PaletteCacheMixin {
    @Shadow
    @Final
    @Mutable
    private Map<Block, List<StructureBlockInfo>> cache;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void trialFinder$useConcurrentBlockIndex(CallbackInfo callbackInfo) {
        this.cache = new ConcurrentHashMap<>(this.cache);
    }
}
