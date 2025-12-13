package hoavinh.mocvien_coffee.dto;

import java.time.LocalDateTime;

public record CafeTableDto(Long id,
                           String name,
                           String status,
                           boolean active,
                           LocalDateTime occupiedAt) {
}

