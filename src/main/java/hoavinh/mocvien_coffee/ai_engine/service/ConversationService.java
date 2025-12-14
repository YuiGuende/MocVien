package hoavinh.mocvien_coffee.ai_engine.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConversationService {

    // In-memory storage (có thể nâng cấp lên Redis sau)
    private final Map<String, ConversationState> conversations = new ConcurrentHashMap<>();
    private static final long SESSION_TIMEOUT_MINUTES = 30;

    /**
     * Lấy hoặc tạo conversation state mới
     */
    public ConversationState getOrCreateConversation(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = generateSessionId();
        }

        return conversations.computeIfAbsent(sessionId, k -> {
            ConversationState state = new ConversationState(k);
            state.setLastActivity(LocalDateTime.now());
            return state;
        });
    }

    /**
     * Thêm items vào cart
     */
    public ConversationState addToCart(String sessionId, List<CartItem> items) {
        ConversationState state = getOrCreateConversation(sessionId);
        
        for (CartItem newItem : items) {
            // Kiểm tra xem sản phẩm đã có trong cart chưa
            boolean found = false;
            for (CartItem existingItem : state.getCartItems()) {
                if (existingItem.getProduct().getId().equals(newItem.getProduct().getId())) {
                    // Cập nhật quantity và note nếu có
                    existingItem.setQuantity(existingItem.getQuantity() + newItem.getQuantity());
                    if (newItem.getNote() != null && !newItem.getNote().isBlank()) {
                        existingItem.setNote(newItem.getNote());
                    }
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                state.getCartItems().add(newItem);
            }
        }
        
        state.updateActivity();
        return state;
    }

    /**
     * Xóa items khỏi cart
     */
    public ConversationState removeFromCart(String sessionId, List<String> productNames) {
        ConversationState state = getOrCreateConversation(sessionId);
        
        state.getCartItems().removeIf(item -> 
            productNames.stream().anyMatch(name -> 
                item.getProduct().getName().equalsIgnoreCase(name)
            )
        );
        
        state.updateActivity();
        return state;
    }

    /**
     * Lấy cart hiện tại
     */
    public List<CartItem> getCart(String sessionId) {
        ConversationState state = conversations.get(sessionId);
        if (state == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(state.getCartItems());
    }

    /**
     * Lấy cart summary dạng text
     */
    public String getCartSummary(String sessionId) {
        ConversationState state = conversations.get(sessionId);
        if (state == null || state.getCartItems().isEmpty()) {
            return "Giỏ hàng của bạn đang trống.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== GIỎ HÀNG CỦA BẠN ===\n\n");
        
        double total = 0;
        for (CartItem item : state.getCartItems()) {
            double subtotal = item.getSubtotal();
            total += subtotal;
            sb.append(String.format("• %s x%d - %s VNĐ", 
                    item.getProduct().getName(), 
                    item.getQuantity(),
                    formatPrice(subtotal)));
            if (item.getNote() != null && !item.getNote().isBlank()) {
                sb.append(" (Ghi chú: ").append(item.getNote()).append(")");
            }
            sb.append("\n");
        }
        
        sb.append("\nTổng cộng: ").append(formatPrice(total)).append(" VNĐ");
        return sb.toString();
    }

    /**
     * Tính tổng tiền cart
     */
    public Double getCartTotal(String sessionId) {
        ConversationState state = conversations.get(sessionId);
        if (state == null || state.getCartItems().isEmpty()) {
            return 0.0;
        }
        return state.getCartItems().stream()
                .mapToDouble(CartItem::getSubtotal)
                .sum();
    }

    /**
     * Xóa conversation (clear cart)
     */
    public void clearConversation(String sessionId) {
        conversations.remove(sessionId);
    }

    /**
     * Set table number cho conversation
     */
    public void setTableNumber(String sessionId, String tableNumber) {
        ConversationState state = getOrCreateConversation(sessionId);
        state.setTableNumber(tableNumber);
        state.updateActivity();
    }

    /**
     * Cleanup expired sessions
     */
    public void cleanupExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        conversations.entrySet().removeIf(entry -> {
            ConversationState state = entry.getValue();
            if (state.getLastActivity() == null) {
                return true;
            }
            long minutesSinceActivity = java.time.Duration.between(state.getLastActivity(), now).toMinutes();
            return minutesSinceActivity > SESSION_TIMEOUT_MINUTES;
        });
    }

    /**
     * Generate session ID
     */
    private String generateSessionId() {
        return "session_" + System.currentTimeMillis() + "_" + 
               (int)(Math.random() * 1000);
    }

    /**
     * Format price
     */
    private String formatPrice(Double price) {
        if (price == null) return "0";
        return String.format("%.0f", price);
    }
}

