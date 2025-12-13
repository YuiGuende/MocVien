package hoavinh.mocvien_coffee.dto;

public record OrderItemRequest(Long productId,
                               int quantity,
                               Double price,
                               String note) {
}

