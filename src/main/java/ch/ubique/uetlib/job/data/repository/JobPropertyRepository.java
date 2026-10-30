package ch.ubique.uetlib.job.data.repository;

import ch.ubique.uetlib.job.model.entity.JobPropertyEntity;
import ch.ubique.uetlib.job.model.entity.JobPropertyId;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobPropertyRepository extends JpaRepository<JobPropertyEntity, JobPropertyId> {

    Stream<JobPropertyEntity> findAllByJobId(int jobId);

    Optional<JobPropertyEntity> findByJobIdAndKey(int jobId, String key);

    void deleteAllByJobId(int jobId);

    void deleteAllByKeyNotIn(Collection<String> jobPropertyName);
}
