package cn.trialfinder.sim.template;

import cn.trialfinder.sim.math.Direction;
import cn.trialfinder.sim.math.FrontAndTop;

/**
 * 复刻 {@code net.minecraft.world.level.block.JigsawBlock} 的静态助手（26.2 语义）。
 */
public final class JigsawBlock {
    private JigsawBlock() {
    }

    public static boolean canAttach(JigsawBlockInfo jigsaw, JigsawBlockInfo candidate) {
        FrontAndTop a = jigsaw.info().state().frontAndTop();
        FrontAndTop b = candidate.info().state().frontAndTop();
        Direction front = a.front();
        Direction candidateFront = b.front();
        Direction top = a.top();
        Direction candidateTop = b.top();
        boolean rollable = jigsaw.jointType() == JointType.ROLLABLE;
        return front == candidateFront.getOpposite()
                && (rollable || top == candidateTop)
                && jigsaw.target().equals(candidate.name());
    }

    public static Direction getFrontFacing(BlockState state) {
        return state.frontAndTop().front();
    }

    public static Direction getTopFacing(BlockState state) {
        return state.frontAndTop().top();
    }
}
