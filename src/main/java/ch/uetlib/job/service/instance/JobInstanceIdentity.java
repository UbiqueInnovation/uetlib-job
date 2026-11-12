package ch.uetlib.job.service.instance;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * This process's instance name, taints and start time; also used by dedicated {@code --job}
 * processes.
 */
@Component
public class JobInstanceIdentity {

    public static final String INSTANCE_NAME_PROP = "ch.uetlib.job.runner.instance.name";
    public static final String INSTANCE_TAINTS_PROP = "ch.uetlib.job.runner.instance.taints";

    private final String name;
    private final Set<String> taints;
    private final Instant startedAt;

    @Autowired
    public JobInstanceIdentity(
            @Value("${" + INSTANCE_NAME_PROP + ":#{null}}") final @Nullable String configuredName,
            @Value("${" + INSTANCE_TAINTS_PROP + ":#{null}}") final @Nullable List<String> taints) {
        this(
                resolveName(configuredName),
                Set.copyOf(Optional.ofNullable(taints).orElse(List.of())),
                // Whole seconds, as stored by every database: the lease ranking compares them.
                Instant.now().truncatedTo(ChronoUnit.SECONDS));
    }

    public JobInstanceIdentity(
            final String name, final Set<String> taints, final Instant startedAt) {
        this.name = name;
        this.taints = Set.copyOf(taints);
        this.startedAt = startedAt;
    }

    /** {@value #INSTANCE_NAME_PROP} if set, else {@code HOSTNAME}, else the local host name. */
    static String resolveName(final @Nullable String configuredName) {
        if (configuredName != null && !configuredName.isBlank()) {
            return configuredName;
        }
        final var hostname = System.getenv("HOSTNAME");
        if (hostname != null && !hostname.isBlank()) {
            return hostname;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (final UnknownHostException e) {
            throw new IllegalStateException(
                    "Cannot determine the job runner instance name: set "
                            + INSTANCE_NAME_PROP
                            + " or the HOSTNAME environment variable.",
                    e);
        }
    }

    public String name() {
        return name;
    }

    public Set<String> taints() {
        return taints;
    }

    public Instant startedAt() {
        return startedAt;
    }
}
