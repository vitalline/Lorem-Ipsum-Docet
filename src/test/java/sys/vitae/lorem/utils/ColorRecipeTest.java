package sys.vitae.lorem.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Color;
import org.junit.jupiter.api.Test;

class ColorRecipeTest {

    @Test
    void retainsRecipeBoundariesAndMixesWithExistingDye() {
        ColorRecipe together = new ColorRecipe(List.of(List.of(Color.RED, Color.BLUE)));
        ColorRecipe successive = new ColorRecipe(List.of(List.of(Color.RED))).append(List.of(Color.BLUE));

        assertEquals(Color.fromRGB(255, 127, 255), together.apply(Color.WHITE));
        assertEquals(Color.fromRGB(169, 84, 255), successive.apply(Color.WHITE));
        assertEquals(Color.fromRGB(255, 0, 255), together.apply(null));
        assertEquals(Color.fromRGB(255, 0, 255), successive.apply(null));
    }

    @Test
    void preservesRepeatedDyesAndRepeatedApplication() {
        ColorRecipe recipe = new ColorRecipe(List.of(List.of(Color.RED, Color.RED, Color.BLUE)));
        assertEquals(Color.fromRGB(255, 0, 127), recipe.apply(null));
        assertNotEquals(recipe.apply(Color.WHITE), recipe.apply(recipe.apply(Color.WHITE)));
    }

    @Test
    void blackDoesNotDivideByZero() {
        assertEquals(Color.BLACK, new ColorRecipe(List.of(List.of(Color.BLACK, Color.BLACK))).apply(Color.BLACK));
    }

    @Test
    void payloadRoundTripPreservesAllStepsAndWeights() {
        ColorRecipe recipe = new ColorRecipe(List.of(List.of(Color.RED, Color.RED), List.of(Color.BLUE, Color.WHITE)));
        assertEquals(recipe, ColorRecipe.decode(recipe.encode()));
        assertEquals(recipe.apply(Color.fromRGB(0x123456)),
                ColorRecipe.decode(recipe.encode()).apply(Color.fromRGB(0x123456)));
    }

    @Test
    void copiedBaseReplacesTheExistingDyeAndLaterStepsBuildOnIt() {
        ColorRecipe copied = ColorRecipe.of(Color.fromRGB(0x123456));
        assertEquals(Color.fromRGB(0x123456), copied.apply(Color.WHITE));
        assertEquals(Color.fromRGB(0x123456), copied.apply(null));
        ColorRecipe dyed = copied.append(List.of(Color.RED));
        assertEquals(new ColorRecipe(List.of(List.of(Color.RED))).apply(Color.fromRGB(0x123456)), dyed.apply(Color.WHITE));

        for (ColorRecipe recipe : List.of(copied, dyed, ColorRecipe.of(Color.BLACK))) {
            assertEquals(recipe, ColorRecipe.decode(recipe.encode()));
        }
    }

    @Test
    void rejectsMalformedPayloads() {
        for (int[] data : List.of(new int[0], new int[] {0}, new int[] {-1}, new int[] {2, 0x123456},
                new int[] {1, -1}, new int[] {0, -1}, new int[] {0, 0x123456, 2, 0x123456})) {
            assertThrows(IllegalArgumentException.class, () -> ColorRecipe.decode(data));
        }
    }

    @Test
    void recipeDoesNotRetainMutableIngredientLists() {
        List<Color> dyes = new ArrayList<>(List.of(Color.RED));
        ColorRecipe recipe = new ColorRecipe(List.of(dyes));
        dyes.add(Color.BLUE);
        assertEquals(Color.RED, recipe.apply(null));
    }
}
