package sys.vitae.lorem;

import java.util.Objects;

import org.jspecify.annotations.NonNull;
import org.mockito.Mockito;
import org.mockito.verification.VerificationMode;

/** Null-checked boundaries for Mockito's unannotated generic return types. */
public final class NonNullMockito {

    private NonNullMockito() {}

    public static <T> @NonNull T mock(Class<T> type) {
        return Objects.requireNonNull(Mockito.mock(type));
    }

    public static <T> @NonNull T verify(T mock) {
        return Objects.requireNonNull(Mockito.verify(mock));
    }

    public static <T> @NonNull T verify(T mock, VerificationMode mode) {
        return Objects.requireNonNull(Mockito.verify(mock, mode));
    }
}
