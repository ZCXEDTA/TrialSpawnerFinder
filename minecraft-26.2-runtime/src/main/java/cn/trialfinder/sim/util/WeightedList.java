package cn.trialfinder.sim.util;

import cn.trialfinder.sim.random.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 复刻 {@code net.minecraft.util.random.WeightedList}（26.2 语义）。
 * {@link #getRandom} 与原版一致：一次 {@code nextInt(totalWeight)} 映射到累计权重区间。
 */
public final class WeightedList<T> {
    private final int totalWeight;
    private final List<Weighted<T>> items;

    public WeightedList(List<? extends Weighted<T>> items) {
        this.items = List.copyOf(items);
        int total = 0;
        for (Weighted<T> item : items) {
            total += item.weight();
        }
        this.totalWeight = total;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public boolean isEmpty() {
        return this.items.isEmpty();
    }

    public Optional<T> getRandom(RandomSource random) {
        if (this.totalWeight == 0) {
            return Optional.empty();
        }
        int index = random.nextInt(this.totalWeight);
        for (Weighted<T> item : this.items) {
            index -= item.weight();
            if (index < 0) {
                return Optional.of(item.value());
            }
        }
        return Optional.empty();
    }

    public T getRandomOrThrow(RandomSource random) {
        if (this.totalWeight == 0) {
            throw new IllegalStateException("带权列表为空");
        }
        return this.getRandom(random).orElseThrow();
    }

    public List<Weighted<T>> unwrap() {
        return this.items;
    }

    public static final class Builder<T> {
        private final List<Weighted<T>> entries = new ArrayList<>();

        public Builder<T> add(T value, int weight) {
            this.entries.add(Weighted.of(value, weight));
            return this;
        }

        public Builder<T> add(T value) {
            return this.add(value, 1);
        }

        public WeightedList<T> build() {
            return new WeightedList<>(this.entries);
        }
    }
}
