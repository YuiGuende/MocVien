package hoavinh.mocvien_coffee.ai_engine.advisors;

import hoavinh.mocvien_coffee.ai_engine.service.ConversationState;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class OrderIntentAdvisor {

    // Keywords cho từng intent
    private static final List<String> GREETING_KEYWORDS = Arrays.asList(
            "xin chào", "chào", "hello", "hi", "chào bạn", "chào em"
    );

    private static final List<String> VIEW_CART_KEYWORDS = Arrays.asList(
            "giỏ hàng", "cart", "đơn hàng", "xem giỏ", "giỏ của tôi", "tôi đã chọn gì"
    );

    private static final List<String> CONFIRM_KEYWORDS = Arrays.asList(
            "xác nhận", "đặt hàng", "order", "ok", "đồng ý", "thanh toán", "checkout", "tôi muốn đặt"
    );

    private static final List<String> CANCEL_KEYWORDS = Arrays.asList(
            "hủy", "cancel", "xóa", "bỏ", "không cần", "thôi"
    );

    private static final List<String> REMOVE_CART_KEYWORDS = Arrays.asList(
            "xóa", "bỏ", "remove", "không cần", "bớt"
    );

    private static final List<String> MENU_QUESTION_KEYWORDS = Arrays.asList(
            "menu", "món", "có gì", "bán gì", "giá", "bao nhiêu", "thế nào"
    );

    /**
     * Detect intent từ message
     */
    public OrderIntent detectIntent(String message, ConversationState state) {
        if (message == null || message.isBlank()) {
            return OrderIntent.UNKNOWN;
        }

        String normalized = normalize(message);

        // Check greeting
        if (isGreetingIntent(normalized)) {
            return OrderIntent.GREETING;
        }

        // Check view cart
        if (isViewCartIntent(normalized)) {
            return OrderIntent.VIEW_CART;
        }

        // Check cancel
        if (isCancelIntent(normalized)) {
            return OrderIntent.CANCEL;
        }

        // Check confirm order
        if (isConfirmIntent(normalized)) {
            return OrderIntent.CONFIRM_ORDER;
        }

        // Check remove from cart (cần có context của cart)
        if (state != null && !state.getCartItems().isEmpty() && isRemoveFromCartIntent(normalized)) {
            return OrderIntent.REMOVE_FROM_CART;
        }

        // Check menu question
        if (isMenuQuestionIntent(normalized)) {
            return OrderIntent.ASK_MENU;
        }

        // Check add to cart (nếu có số lượng hoặc từ khóa order)
        if (isAddToCartIntent(normalized)) {
            return OrderIntent.ADD_TO_CART;
        }

        // Default: nếu có cart items và message không rõ ràng, coi như add to cart
        if (state != null && !state.getCartItems().isEmpty()) {
            // Có thể là update quantity hoặc add more
            if (normalized.matches(".*\\d+.*")) {
                return OrderIntent.UPDATE_QUANTITY;
            }
        }

        return OrderIntent.UNKNOWN;
    }

    /**
     * Check greeting intent
     */
    public boolean isGreetingIntent(String message) {
        String normalized = normalize(message);
        return GREETING_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    /**
     * Check view cart intent
     */
    public boolean isViewCartIntent(String message) {
        String normalized = normalize(message);
        return VIEW_CART_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    /**
     * Check confirm intent
     */
    public boolean isConfirmIntent(String message) {
        String normalized = normalize(message);
        return CONFIRM_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    /**
     * Check cancel intent
     */
    public boolean isCancelIntent(String message) {
        String normalized = normalize(message);
        return CANCEL_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    /**
     * Check remove from cart intent
     */
    public boolean isRemoveFromCartIntent(String message) {
        String normalized = normalize(message);
        return REMOVE_CART_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    /**
     * Check menu question intent
     */
    public boolean isMenuQuestionIntent(String message) {
        String normalized = normalize(message);
        return MENU_QUESTION_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    /**
     * Check add to cart intent (có số lượng hoặc từ khóa order)
     */
    public boolean isAddToCartIntent(String message) {
        String normalized = normalize(message);
        // Nếu có số lượng (1, 2, 3...) hoặc từ "cho tôi", "tôi muốn", "lấy"
        return normalized.matches(".*\\d+.*") ||
               normalized.contains("cho tôi") ||
               normalized.contains("tôi muốn") ||
               normalized.contains("lấy") ||
               normalized.contains("mua") ||
               normalized.contains("đặt");
    }

    /**
     * Normalize message
     */
    private String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase()
                .replaceAll("[àáạảãâầấậẩẫăằắặẳẵ]", "a")
                .replaceAll("[èéẹẻẽêềếệểễ]", "e")
                .replaceAll("[ìíịỉĩ]", "i")
                .replaceAll("[òóọỏõôồốộổỗơờớợởỡ]", "o")
                .replaceAll("[ùúụủũưừứựửữ]", "u")
                .replaceAll("[ỳýỵỷỹ]", "y")
                .replaceAll("[đ]", "d")
                .trim();
    }
}

