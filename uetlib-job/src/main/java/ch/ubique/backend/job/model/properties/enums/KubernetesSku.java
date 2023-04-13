package ch.ubique.backend.job.model.properties.enums;

public enum KubernetesSku {
    HIGH_MEMORY("highmem"),
    HIGH_PERFORMANCE("highperf");

    private final String value;

    KubernetesSku(final String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
