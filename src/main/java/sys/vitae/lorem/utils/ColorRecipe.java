package sys.vitae.lorem.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import org.bukkit.Color;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Keeps recipe boundaries: mixing two dyes at once is not the same as dyeing twice.
 * A copied color is an absolute base: the steps start from it instead of the target's existing dye.
 */
public record ColorRecipe(@Nullable Color base, List<List<Color>> steps) {

    /** Marks an encoded base color; a step can't be empty, so this never starts a plain recipe. */
    private static final int BASE_MARKER = 0;

    public ColorRecipe {
        if ((base == null && steps.isEmpty()) || steps.stream().anyMatch(step -> Objects.requireNonNull(step).isEmpty())) {
            throw new IllegalArgumentException("A color recipe must contain a base color or non-empty dyeing steps");
        }
        steps = steps.stream().map(List::copyOf).toList();
    }

    public ColorRecipe(List<List<Color>> steps) {
        this(null, steps);
    }

    public static @NonNull ColorRecipe of(Color base) {
        return new ColorRecipe(Objects.requireNonNull(base), List.of());
    }

    public @NonNull ColorRecipe append(List<Color> dyes) {
        List<List<Color>> result = new ArrayList<>(steps);
        result.add(dyes);
        return new ColorRecipe(base, result);
    }

    public @NonNull Color apply(@Nullable Color original) {
        Color result = base != null ? base : original;
        for (List<Color> step : steps) {
            List<Color> colors = new ArrayList<>(step);
            if (result != null) {
                colors.add(result);
            }
            result = mix(colors);
        }
        return Objects.requireNonNull(result);
    }

    private static @NonNull Color mix(List<Color> colors) {
        int red = 0, green = 0, blue = 0, brightness = 0;
        for (Color color : colors) {
            red += color.getRed();
            green += color.getGreen();
            blue += color.getBlue();
            brightness += Math.max(color.getRed(), Math.max(color.getGreen(), color.getBlue()));
        }
        int count = colors.size();
        red /= count;
        green /= count;
        blue /= count;
        float averageBrightness = (float) brightness / count;
        float maximum = Math.max(red, Math.max(green, blue));
        if (maximum == 0) {
            return Objects.requireNonNull(Color.BLACK);
        }
        return Objects.requireNonNull(Color.fromRGB((int) (red * averageBrightness / maximum),
                (int) (green * averageBrightness / maximum), (int) (blue * averageBrightness / maximum)));
    }

    public int[] encode() {
        IntStream prefix = base == null ? IntStream.empty() : IntStream.of(BASE_MARKER, base.asRGB());
        return IntStream.concat(prefix, steps.stream().flatMapToInt(step -> IntStream.concat(IntStream.of(step.size()),
                step.stream().mapToInt(color -> Objects.requireNonNull(color).asRGB())))).toArray();
    }

    public static @NonNull ColorRecipe decode(int[] data) {
        Color base = null;
        int start = 0;
        if (data.length >= 2 && data[0] == BASE_MARKER) {
            base = Color.fromRGB(data[1]);
            start = 2;
        }
        List<List<Color>> steps = new ArrayList<>();
        for (int i = start; i < data.length;) {
            int count = data[i++];
            if (count < 1 || count > data.length - i) {
                throw new IllegalArgumentException("Invalid template color recipe step length: " + count);
            }
            List<Color> colors = new ArrayList<>();
            for (int end = i + count; i < end; i++) {
                colors.add(Color.fromRGB(data[i]));
            }
            steps.add(colors);
        }
        return new ColorRecipe(base, steps);
    }
}
