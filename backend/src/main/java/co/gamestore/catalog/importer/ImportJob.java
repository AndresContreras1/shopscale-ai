package co.gamestore.catalog.importer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Progress of a bulk import. Stored in the database, not in memory, because the status request may be
 * answered by a different API replica than the one processing the file.
 */
@Entity
@Table(name = "import_jobs")
@Getter
@Setter
@NoArgsConstructor
public class ImportJob {

    public enum Status { PENDING, RUNNING, COMPLETED, FAILED }

    @Id
    @Column(length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    private String fileName;

    private int totalRows;

    private int processedRows;

    private int created;

    private int updated;

    private int failed;

    @Column(length = 4000)
    private String errors;

    @Column(nullable = false, length = 120)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant finishedAt;

    public ImportJob(String id, String fileName, int totalRows, String createdBy) {
        this.id = id;
        this.fileName = fileName;
        this.totalRows = totalRows;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }
}
