package cn.trialfinder.sim.climate;

import cn.trialfinder.sim.math.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 复刻 {@code net.minecraft.world.level.levelgen.synth.Climate}（26.2 语义）：
 * 参数点/目标点量化、{@link ParameterList} 的 RTree 最近邻查找。
 *
 * <p>RTree 的 {@code ThreadLocal lastResult} 在单线程模拟里退化为实例字段；
 * 结果与热启动无关（子树包围盒距离是叶子的下界，剪枝安全），只是命中同一个叶子的加速。
 */
public final class Climate {
    private Climate() {
    }

    public static long quantizeCoord(float value) {
        return (long) (value * 10000.0f);
    }

    public static float unquantizeCoord(long value) {
        return (float) value / 10000.0f;
    }

    public static TargetPoint target(
            float temperature, float humidity, float continentalness,
            float erosion, float depth, float weirdness) {
        return new TargetPoint(
                quantizeCoord(temperature), quantizeCoord(humidity), quantizeCoord(continentalness),
                quantizeCoord(erosion), quantizeCoord(depth), quantizeCoord(weirdness));
    }

    public record Pair<A, B>(A first, B second) {
    }

    public record Parameter(long min, long max) {
        public static Parameter point(float value) {
            return span(value, value);
        }

        public static Parameter span(float min, float max) {
            if (min > max) {
                throw new IllegalArgumentException("min=" + min + " max=" + max);
            }
            return new Parameter(quantizeCoord(min), quantizeCoord(max));
        }

        public Parameter span(Parameter other) {
            if (other == null) {
                return this;
            }
            return new Parameter(Math.min(this.min, other.min), Math.max(this.max, other.max));
        }

        public long distance(long target) {
            long d1 = target - this.max;
            long d2 = this.min - target;
            return d1 > 0 ? d1 : Math.max(d2, 0);
        }
    }

    public record ParameterPoint(
            Parameter temperature, Parameter humidity, Parameter continentalness,
            Parameter erosion, Parameter depth, Parameter weirdness, long offset) {

        public long fitness(TargetPoint target) {
            return Mth.square(this.temperature.distance(target.temperature()))
                    + Mth.square(this.humidity.distance(target.humidity()))
                    + Mth.square(this.continentalness.distance(target.continentalness()))
                    + Mth.square(this.erosion.distance(target.erosion()))
                    + Mth.square(this.depth.distance(target.depth()))
                    + Mth.square(this.weirdness.distance(target.weirdness()))
                    + Mth.square(this.offset);
        }

        public List<Parameter> parameterSpace() {
            return List.of(
                    this.temperature, this.humidity, this.continentalness,
                    this.erosion, this.depth, this.weirdness,
                    new Parameter(this.offset, this.offset));
        }
    }

    public record TargetPoint(
            long temperature, long humidity, long continentalness,
            long erosion, long depth, long weirdness) {

        long[] toParameterArray() {
            return new long[]{
                    this.temperature, this.humidity, this.continentalness,
                    this.erosion, this.depth, this.weirdness, 0L};
        }
    }

    public static final class Sampler {
        private final DensityFunction temperature;
        private final DensityFunction humidity;
        private final DensityFunction continentalness;
        private final DensityFunction erosion;
        private final DensityFunction depth;
        private final DensityFunction weirdness;

        public Sampler(
                DensityFunction temperature, DensityFunction humidity,
                DensityFunction continentalness, DensityFunction erosion,
                DensityFunction depth, DensityFunction weirdness) {
            this.temperature = temperature;
            this.humidity = humidity;
            this.continentalness = continentalness;
            this.erosion = erosion;
            this.depth = depth;
            this.weirdness = weirdness;
        }

        /** 按方块坐标采样 6 维目标点；坐标先按四分之一块取整（>>2 再 <<2）。 */
        public TargetPoint sample(int blockX, int blockY, int blockZ) {
            int quartX = blockX >> 2;
            int quartY = blockY >> 2;
            int quartZ = blockZ >> 2;
            SinglePointContext context = new SinglePointContext(
                    quartX << 2, quartY << 2, quartZ << 2);
            return Climate.target(
                    (float) this.temperature.compute(context),
                    (float) this.humidity.compute(context),
                    (float) this.continentalness.compute(context),
                    (float) this.erosion.compute(context),
                    (float) this.depth.compute(context),
                    (float) this.weirdness.compute(context));
        }
    }

    public static class ParameterList<T> {
        private final List<Pair<ParameterPoint, T>> values;
        private final RTree<T> index;

        public ParameterList(List<Pair<ParameterPoint, T>> values) {
            this.values = values;
            this.index = RTree.create(values);
        }

        public List<Pair<ParameterPoint, T>> values() {
            return this.values;
        }

        public T findValue(TargetPoint targetPoint) {
            return this.findValueIndex(targetPoint);
        }

        protected T findValueIndex(TargetPoint targetPoint) {
            return this.index.search(targetPoint, DistanceMetric.defaultMetric());
        }

        public T findValueBruteForce(TargetPoint targetPoint) {
            Pair<ParameterPoint, T> best = null;
            long bestFitness = Long.MAX_VALUE;
            for (Pair<ParameterPoint, T> pair : this.values) {
                long fitness = pair.first().fitness(targetPoint);
                if (bestFitness > fitness) {
                    bestFitness = fitness;
                    best = pair;
                }
            }
            return best == null ? null : best.second();
        }
    }

    @FunctionalInterface
    private interface DistanceMetric<T> {
        long distance(Node<T> node, long[] params);

        static <T> DistanceMetric<T> defaultMetric() {
            return Node::distance;
        }
    }

    private abstract static class Node<T> {
        final Parameter[] parameterSpace;

        protected Node(List<Parameter> parameterSpace) {
            this.parameterSpace = parameterSpace.toArray(new Parameter[0]);
        }

        abstract Leaf<T> search(long[] params, Leaf<T> currentBest, DistanceMetric<T> metric);

        long distance(long[] params) {
            long distance = 0L;
            for (int i = 0; i < 7; i++) {
                distance += Mth.square(this.parameterSpace[i].distance(params[i]));
            }
            return distance;
        }
    }

    private static final class Leaf<T> extends Node<T> {
        final T value;

        private Leaf(ParameterPoint point, T value) {
            super(point.parameterSpace());
            this.value = value;
        }

        @Override
        Leaf<T> search(long[] params, Leaf<T> currentBest, DistanceMetric<T> metric) {
            return this;
        }
    }

    private static final class SubTree<T> extends Node<T> {
        final Node<T>[] children;

        SubTree(List<Node<T>> children) {
            this(RTree.buildParameterSpace(children), children);
        }

        SubTree(List<Parameter> parameterSpace, List<Node<T>> children) {
            super(parameterSpace);
            this.children = children.toArray(new Node[0]);
        }

        @Override
        Leaf<T> search(long[] params, Leaf<T> currentBest, DistanceMetric<T> metric) {
            long bestDist =
                    currentBest == null ? Long.MAX_VALUE : metric.distance(currentBest, params);
            Leaf<T> bestLeaf = currentBest;
            for (Node<T> child : this.children) {
                long cd = metric.distance(child, params);
                if (bestDist > cd) {
                    Leaf<T> leaf = child.search(params, bestLeaf, metric);
                    long r = child == leaf ? cd : metric.distance(leaf, params);
                    if (bestDist > r) {
                        bestDist = r;
                        bestLeaf = leaf;
                    }
                }
            }
            return bestLeaf;
        }
    }

    private static final class RTree<T> {
        private final Node<T> root;
        private Leaf<T> lastResult;

        private RTree(Node<T> root) {
            this.root = root;
        }

        static <T> RTree<T> create(List<Pair<ParameterPoint, T>> list) {
            if (list.isEmpty()) {
                throw new IllegalArgumentException("Need at least one value to build the search tree.");
            }
            int size = list.get(0).first().parameterSpace().size();
            if (size != 7) {
                throw new IllegalStateException("Expecting parameter space of 7, but got " + size);
            }
            List<Leaf<T>> leaves = new ArrayList<>();
            for (Pair<ParameterPoint, T> pair : list) {
                leaves.add(new Leaf<>(pair.first(), pair.second()));
            }
            return new RTree<>(build(7, leaves));
        }

        T search(TargetPoint target, DistanceMetric<T> metric) {
            long[] params = target.toParameterArray();
            Leaf<T> leaf = this.root.search(params, this.lastResult, metric);
            this.lastResult = leaf;
            return leaf.value;
        }

        private static <T> Node<T> build(int depth, List<? extends Node<T>> list) {
            if (list.isEmpty()) {
                throw new IllegalStateException("Need at least one child to build a node");
            }
            if (list.size() == 1) {
                return list.get(0);
            }
            if (list.size() <= 6) {
                List<Node<T>> sorted = new ArrayList<>(list);
                sorted.sort(Comparator.comparingLong(node -> sumAbsMid(node, depth)));
                return new SubTree<>(sorted);
            }
            long bestCost = Long.MAX_VALUE;
            int bestDim = -1;
            List<SubTree<T>> bestBuckets = null;
            for (int dim = 0; dim < depth; dim++) {
                List<Node<T>> sorted = new ArrayList<>(list);
                sort(sorted, depth, dim, false);
                List<SubTree<T>> buckets = bucketize(sorted);
                long cost = 0L;
                for (SubTree<T> bucket : buckets) {
                    cost += cost(bucket.parameterSpace);
                }
                if (bestCost > cost) {
                    bestCost = cost;
                    bestDim = dim;
                    bestBuckets = buckets;
                }
            }
            List<Node<T>> children = new ArrayList<>();
            for (SubTree<T> bucket : bestBuckets) {
                children.add(build(depth, java.util.Arrays.asList(bucket.children)));
            }
            return new SubTree<>(children);
        }

        private static <T> long sumAbsMid(Node<T> node, int depth) {
            long sum = 0L;
            for (int i = 0; i < depth; i++) {
                sum += Math.abs((node.parameterSpace[i].min() + node.parameterSpace[i].max()) / 2);
            }
            return sum;
        }

        private static <T> void sort(
                List<Node<T>> list, int depth, int dim, boolean sortOrder) {
            Comparator<Node<T>> comparator = comparator(dim, sortOrder);
            for (int i = 1; i < depth; i++) {
                comparator = comparator.thenComparing(comparator((dim + i) % depth, sortOrder));
            }
            list.sort(comparator);
        }

        private static <T> Comparator<Node<T>> comparator(int dim, boolean sortOrder) {
            return Comparator.comparingLong(node -> {
                long mid = (node.parameterSpace[dim].min() + node.parameterSpace[dim].max()) / 2;
                return sortOrder ? Math.abs(mid) : mid;
            });
        }

        private static <T> List<SubTree<T>> bucketize(List<Node<T>> nodes) {
            List<SubTree<T>> buckets = new ArrayList<>();
            List<Node<T>> current = new ArrayList<>();
            int bucketSize = (int) Math.pow(
                    6.0D, Math.floor(Math.log((double) nodes.size() - 0.01D) / Math.log(6.0D)));
            for (Node<T> node : nodes) {
                current.add(node);
                if (current.size() >= bucketSize) {
                    buckets.add(new SubTree<>(current));
                    current = new ArrayList<>();
                }
            }
            if (!current.isEmpty()) {
                buckets.add(new SubTree<>(current));
            }
            return buckets;
        }

        private static long cost(Parameter[] parameterSpace) {
            long total = 0L;
            for (Parameter parameter : parameterSpace) {
                total += Math.abs(parameter.max() - parameter.min());
            }
            return total;
        }

        private static <T> List<Parameter> buildParameterSpace(List<Node<T>> nodes) {
            if (nodes.isEmpty()) {
                throw new IllegalArgumentException("SubTree needs at least one child");
            }
            List<Parameter> space = new ArrayList<>(7);
            for (int i = 0; i < 7; i++) {
                space.add(null);
            }
            for (Node<T> node : nodes) {
                for (int i = 0; i < 7; i++) {
                    space.set(i, node.parameterSpace[i].span(space.get(i)));
                }
            }
            return space;
        }
    }
}
