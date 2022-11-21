package ch.ubique.backend.job.model;

import ch.ubique.backend.job.BaseInputJob;
import ch.ubique.backend.job.BaseJob;
import com.pivovarit.function.exception.WrappedException;

public record Job(
        Integer id,
        String clazzName,
        String databaseCronExpression
) {

    public static Job withId(int id, Job job) {
        return new Job(
                id,
                job.clazzName(),
                job.databaseCronExpression()
        );
    }

    public static Job fromClass(Class<? extends BaseJob> clazz) {
        return new Job(null, clazz.getCanonicalName(), null);
    }

    public String name() {
        return clazz().getSimpleName();
    }

    public Class<? extends BaseJob> clazz() {
        try {
            //noinspection unchecked
            return (Class<? extends BaseJob>) Class.forName(clazzName());
        } catch (ClassNotFoundException | ClassCastException e) {
            throw new WrappedException(e); // Rethrow as runtime exception
        }
    }

    public boolean isForceRunnable() {
        return BaseInputJob.class.isAssignableFrom(clazz());
    }
}
