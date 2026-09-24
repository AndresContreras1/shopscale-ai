package co.gamestore.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicIdTest {

    @Test
    void generatesVersion7WithTheCorrectVariant() {
        UUID id = PublicId.next();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void carriesTheCreationTime() {
        Instant before = Instant.now().minusMillis(1);

        Instant timestamp = PublicId.timestampOf(PublicId.next());

        assertThat(timestamp).isBetween(before, Instant.now().plusMillis(1));
    }

    @Test
    void idsFromLaterMillisecondsSortAfterEarlierOnes() {
        List<String> ordered = new ArrayList<>();
        for (long millis = 1_700_000_000_000L; millis < 1_700_000_000_005L; millis++) {
            ordered.add(PublicId.from(millis).toString());
        }

        assertThat(ordered).isSorted();
    }

    @Test
    void doesNotRepeatWithinTheSameMillisecond() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(PublicId.from(1_700_000_000_000L));
        }

        assertThat(ids).hasSize(10_000);
    }
}
