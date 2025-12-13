package hoavinh.mocvien_coffee.service;

import hoavinh.mocvien_coffee.model.CafeTable;
import hoavinh.mocvien_coffee.model.TableStatus;
import hoavinh.mocvien_coffee.repository.CafeTableRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TableService {

    private final CafeTableRepository tableRepository;

    public TableService(CafeTableRepository tableRepository) {
        this.tableRepository = tableRepository;
    }

    public List<CafeTable> findActive() {
        return tableRepository.findByActiveTrueOrderByNameAsc();
    }

    public List<CafeTable> findAll() {
        return tableRepository.findAll();
    }

    public CafeTable getById(Long id) {
        return tableRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Table not found"));
    }

    public CafeTable save(CafeTable table) {
        if (table.getStatus() == null) {
            table.setStatus(TableStatus.AVAILABLE);
        }
        if (!table.isActive()) {
            table.setStatus(TableStatus.DISABLED);
            table.setOccupiedAt(null);
        }
        return tableRepository.save(table);
    }

    public void delete(Long id) {
        tableRepository.deleteById(id);
    }

    @Transactional
    public CafeTable occupy(Long id) {
        CafeTable table = getById(id);
        if (table.getStatus() == TableStatus.DISABLED) {
            throw new IllegalStateException("Table is disabled");
        }
        if (table.getStatus() != TableStatus.OCCUPIED) {
            table.setStatus(TableStatus.OCCUPIED);
            table.setOccupiedAt(LocalDateTime.now());
        }
        return table;
    }

    @Transactional
    public CafeTable release(Long id) {
        CafeTable table = getById(id);
        table.setStatus(TableStatus.AVAILABLE);
        table.setOccupiedAt(null);
        return table;
    }
}

