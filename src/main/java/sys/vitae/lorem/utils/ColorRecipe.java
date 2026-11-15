package sys.vitae.lorem.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Color;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Keeps recipe boundaries: mixing two dyes at once is not the same as dyeing twice. */
public record ColorRecipe(List<List<Color>> steps) {

    public ColorRecipe {
        if (steps.isEmpty() || steps.stream().anyMatch(step -> Objects.requireNonNull(step).isEmpty())) {
            throw new IllegalArgumentException("A color recipe must contain nonempty dyeing steps");
        }
        steps = steps.stream().map(List::copyOf).toList();
    }

    public @NonNull ColorRecipe append(List<Color> dyes) {
        List<List<Color>> result = new ArrayList<>(steps);
        result.add(dyes);
        return new ColorRecipe(result);
    }

    public @NonNull Color apply(@Nullable Color original) {
        Color result = original;
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
        return steps.stream().flatMapToInt(step -> java.util.stream.IntStream.concat(
                java.util.stream.IntStream.of(step.size()),
                step.stream().mapToInt(color -> Objects.requireNonNull(color).asRGB()))).toArray();
    }

    public static @NonNull ColorRecipe decode(int[] data) {
        List<List<Color>> steps = new ArrayList<>();
        for (int i = 0; i < data.length;) {
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
        return new ColorRecipe(steps);
    }
}
