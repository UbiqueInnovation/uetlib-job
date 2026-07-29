package ch.ubique.backend.job.service.watcher;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;

public class LineWithEndingReader extends PushbackReader {

    private final StringBuilder lineBuilder;

    public LineWithEndingReader(final Reader reader) {
        super(reader);
        lineBuilder = new StringBuilder();
    }

    public record LineWithEnding(String text, boolean isNewLine) {}

    public @Nullable LineWithEnding readLineWithEnding() throws IOException {
        lineBuilder.setLength(0);

        while (true) {
            final var ch = read();

            if (ch == -1) { // Done reading
                return lineBuilder.isEmpty()
                        ? null
                        : new LineWithEnding(lineBuilder.toString(), true);
            }
            if (ch == '\r') { // Received \r
                return handleCarriageReturn();
            }
            if (ch == '\n') { // Received \n
                return new LineWithEnding(lineBuilder.toString(), true);
            }

            lineBuilder.append((char) ch);
        }
    }

    private LineWithEnding handleCarriageReturn() throws IOException {
        final var next = read();
        final var lineText = lineBuilder.toString();
        if (next == '\n') {
            return new LineWithEnding(lineText, true);
        }
        if (next != -1) {
            unread(next);
            return new LineWithEnding(lineText, false);
        }
        return new LineWithEnding(lineText, true);
    }
}
