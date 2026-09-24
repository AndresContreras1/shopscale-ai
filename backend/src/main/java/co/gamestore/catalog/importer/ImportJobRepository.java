package co.gamestore.catalog.importer;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportJobRepository extends JpaRepository<ImportJob, String> {
}
