package hoavinh.mocvien_coffee.dto;

public record CartItemDto(
        Long productId,
        String productName,
        int quantity,
        Double price,
        String note
) {
}

