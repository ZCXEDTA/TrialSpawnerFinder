package cn.trialfinder.sim.structure;

/**
 * Port of net.minecraft.world.level.block.JigsawBlock (1.21.11) — the static helpers only.
 */
public final class JigsawBlock {
    private JigsawBlock() {
    }

    public static boolean canAttach(JigsawBlockInfo jigsaw, JigsawBlockInfo candidate) {
        Direction front = getFrontFacing(jigsaw.info().state());
        Direction candidateFront = getFrontFacing(candidate.info().state());
        Direction top = getTopFacing(jigsaw.info().state());
        Direction candidateTop = getTopFacing(candidate.info().state());
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
