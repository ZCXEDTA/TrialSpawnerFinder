package cn.trialfinder.mixin;

import net.minecraft.block.Block;
import net.minecraft.structure.StructureTemplate;
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

@Mixin(StructureTemplate.PalettedBlockInfoList.class)
public abstract class PalettedBlockInfoListMixin {
    @Shadow
    @Final
    @Mutable
    private Map<Block, List<StructureTemplate.StructureBlockInfo>> blockToInfos;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void trialFinder$useConcurrentBlockIndex(
            List<StructureTemplate.StructureBlockInfo> infos, CallbackInfo callbackInfo) {
        blockToInfos = new ConcurrentHashMap<>(blockToInfos);
    }
}
