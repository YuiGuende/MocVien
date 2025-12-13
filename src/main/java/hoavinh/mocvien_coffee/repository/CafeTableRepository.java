package hoavinh.mocvien_coffee.repository;

import hoavinh.mocvien_coffee.model.CafeTable;
import hoavinh.mocvien_coffee.model.TableStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CafeTableRepository extends JpaRepository<CafeTable, Long> {
    List<CafeTable> findByActiveTrueOrderByNameAsc();
    Optional<CafeTable> findByName(String name);
    long countByStatus(TableStatus status);
}

