package hoavinh.mocvien_coffee.ai_engine.tools;

import hoavinh.mocvien_coffee.ai_engine.service.CartItem;
import hoavinh.mocvien_coffee.ai_engine.service.ConversationService;
import hoavinh.mocvien_coffee.ai_engine.service.QdrantMenuService;
import hoavinh.mocvien_coffee.model.Product;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * AI Tools để LLM có thể gọi
 * LLM sẽ tự quyết định khi nào gọi tool nào
 * 
 * Note: Spring AI 1.1.1 sử dụng Function beans để register tools
 */
@Configuration
public class AiOrderTools {

    private final QdrantMenuService qdrantMenuService;
    private final ConversationService conversationService;

    public AiOrderTools(QdrantMenuService qdrantMenuService, 
                       ConversationService conversationService) {
        this.qdrantMenuService = qdrantMenuService;
        this.conversationService = conversationService;
    }

    /**
     * Tool: getMenu - Tìm kiếm menu items
     * LLM gọi tool này khi khách hỏi về menu hoặc tìm món
     */
    @Bean
    public Function<String, String> getMenu() {
        return query -> {
        if (query == null || query.isBlank()) {
            return "Vui lòng cho em biết anh/chị muốn tìm món gì ạ?";
        }

        // Search trong Qdrant
        List<Product> products = qdrantMenuService.searchProductsInQdrant(query, 5);

        if (products.isEmpty()) {
            return String.format("Em không tìm thấy món nào liên quan đến '%s'. " +
                    "Anh/chị có thể mô tả rõ hơn không ạ?", query);
        }

        StringBuilder result = new StringBuilder("Em tìm thấy các món sau:\n\n");
        for (Product product : products) {
            result.append(String.format("• %s - %s VNĐ (%s)\n",
                    product.getName(),
                    formatPrice(product.getPrice()),
                    product.getCategory()));
        }

        return result.toString();
        };
    }

    /**
     * Tool: addToCart - Thêm món vào giỏ hàng
     * LLM gọi tool này khi khách muốn đặt món
     * Note: Spring AI functions chỉ hỗ trợ single parameter, cần dùng Map hoặc wrapper
     */
    @Bean
    public Function<AddToCartRequest, String> addToCart() {
        return request -> {
            String itemName = request.itemName();
            Integer quantity = request.quantity() != null ? request.quantity() : 1;
            String sessionId = request.sessionId();
        if (itemName == null || itemName.isBlank()) {
            return "Em chưa hiểu anh/chị muốn đặt món gì. Anh/chị có thể nói rõ tên món không ạ?";
        }

        if (quantity == null || quantity <= 0) {
            quantity = 1; // Default quantity
        }

        if (sessionId == null || sessionId.isBlank()) {
            return "Lỗi: Không tìm thấy session. Vui lòng thử lại.";
        }

        // Tìm product trong Qdrant
        List<Product> products = qdrantMenuService.searchProductsInQdrant(itemName, 1);
        
        if (products.isEmpty()) {
            return String.format("Em không tìm thấy món '%s'. " +
                    "Anh/chị có thể xem menu hoặc nói lại tên món không ạ?", itemName);
        }

        Product product = products.get(0);
        
        if (!product.isAvailable()) {
            return String.format("Xin lỗi, món '%s' hiện đang hết hàng ạ.", product.getName());
        }

        // Thêm vào cart
        CartItem cartItem = new CartItem(product, quantity, null, product.getPrice());
        conversationService.addToCart(sessionId, List.of(cartItem));

        return String.format("✅ Em đã thêm %d %s vào giỏ hàng (Giá: %s VNĐ). " +
                "Anh/chị muốn thêm món nữa không ạ?",
                quantity, product.getName(), formatPrice(cartItem.getSubtotal()));
        };
    }

    /**
     * Tool: viewCart - Xem giỏ hàng
     * LLM gọi tool này khi khách muốn xem giỏ hàng
     */
    @Bean
    public Function<String, String> viewCart() {
        return sessionId -> {
        if (sessionId == null || sessionId.isBlank()) {
            return "Lỗi: Không tìm thấy session. Vui lòng thử lại.";
        }

        String cartSummary = conversationService.getCartSummary(sessionId);
        Double total = conversationService.getCartTotal(sessionId);

        if (total == null || total == 0) {
            return "Giỏ hàng của anh/chị đang trống. Anh/chị muốn xem menu không ạ?";
        }

        return cartSummary + "\n\nAnh/chị muốn đặt hàng không ạ? Nói 'đặt hàng' hoặc 'xác nhận' để hoàn tất.";
        };
    }

    /**
     * Tool: removeFromCart - Xóa món khỏi giỏ hàng
     */
    @Bean
    public Function<RemoveFromCartRequest, String> removeFromCart() {
        return request -> {
            String itemName = request.itemName();
            String sessionId = request.sessionId();
        if (itemName == null || itemName.isBlank()) {
            return "Em chưa hiểu anh/chị muốn xóa món gì. Anh/chị có thể nói rõ tên món không ạ?";
        }

        if (sessionId == null || sessionId.isBlank()) {
            return "Lỗi: Không tìm thấy session. Vui lòng thử lại.";
        }

        // Lấy cart hiện tại
        var cartItems = conversationService.getCart(sessionId);
        List<String> productNames = new ArrayList<>();
        
        for (var cartItem : cartItems) {
            if (cartItem.getProduct().getName().toLowerCase().contains(itemName.toLowerCase()) ||
                itemName.toLowerCase().contains(cartItem.getProduct().getName().toLowerCase())) {
                productNames.add(cartItem.getProduct().getName());
            }
        }

        if (productNames.isEmpty()) {
            return String.format("Em không tìm thấy món '%s' trong giỏ hàng ạ.", itemName);
        }

        conversationService.removeFromCart(sessionId, productNames);
        return String.format("✅ Em đã xóa '%s' khỏi giỏ hàng. Anh/chị muốn xem lại giỏ hàng không ạ?", 
                String.join(", ", productNames));
        };
    }

    /**
     * Format price
     */
    private String formatPrice(Double price) {
        if (price == null) return "0";
        return String.format("%.0f", price);
    }

    // Request DTOs cho functions
    public record AddToCartRequest(String itemName, Integer quantity, String sessionId) {}
    public record RemoveFromCartRequest(String itemName, String sessionId) {}
}

