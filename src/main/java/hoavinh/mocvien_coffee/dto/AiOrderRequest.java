package hoavinh.mocvien_coffee.dto;

public record AiOrderRequest(
        String message,
        String sessionId,
        String tableNumber
) {
}

