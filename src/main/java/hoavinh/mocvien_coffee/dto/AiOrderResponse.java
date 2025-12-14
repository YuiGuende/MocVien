package hoavinh.mocvien_coffee.dto;

import hoavinh.mocvien_coffee.ai_engine.advisors.OrderIntent;

import java.util.List;

public record AiOrderResponse(
        String message,
        OrderIntent intent,
        List<CartItemDto> cartItems,
        Double totalAmount,
        Long orderId,
        boolean requiresConfirmation
) {
}

