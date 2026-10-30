package ch.ubique.uetlib.job.service.watcher;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ubique.uetlib.job.service.watcher.LineWithEndingReader.LineWithEnding;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class LineWithEndingReaderTest {

    @Test
    void test() throws IOException {
        final var actual = new ArrayList<LineWithEnding>();
        try (final var reader = new LineWithEndingReader(new StringReader("a\r\nb\nc\rd\r"))) {
            LineWithEnding line;
            while ((line = reader.readLineWithEnding()) != null) {
                actual.add(line);
            }
        }

        assertThat(actual)
                .containsExactly(
                        new LineWithEnding("a", true),
                        new LineWithEnding("b", true),
                        new LineWithEnding("c", false),
                        new LineWithEnding("d", true));
    }
}
