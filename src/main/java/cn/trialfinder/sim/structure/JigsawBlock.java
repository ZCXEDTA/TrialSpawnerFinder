package cn.trialfinder.sim.structure;

/**
 * Port of net.minecraft.world.level.block.JigsawBlock (1.21.11) — the static helpers only.
 */
public final class JigsawBlock {
    private JigsawBlock() {
    }

    public static boolean canAttach(JigsawBlockInfo jigsaw, JigsawBlockInfo candidate) {
        // One frontAndTop() lookup per state (the ConcurrentHashMap get is the dominant cost here;
        // resolving both directions from a single lookup halves the lookups vs the per-direction
        // getFrontFacing/getTopFacing helpers).
        FrontAndTop a = jigsaw.info().state().frontAndTop();
        FrontAndTop b = candidate.info().state().frontAndTop();
        Direction front = a.front();
        Direction candidateFront = b.front();
        Direction top = a.top();
        Direction candidateTop = b.top();
        JointType jointType = jigsaw.jointType();
        boolean rollable = jointType == JointType.ROLLABLE;
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
