package ch.ubique.backend.job.model.client;

import java.util.List;

public record JobIdList(List<Integer> ids, boolean include) {
    public boolean isEmpty() {
        return ids.isEmpty();
    }
}
