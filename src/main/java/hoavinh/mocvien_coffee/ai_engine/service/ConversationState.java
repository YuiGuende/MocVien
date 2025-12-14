package hoavinh.mocvien_coffee.ai_engine.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConversationState {
    private String sessionId;
    private List<CartItem> cartItems;
    private String tableNumber;
    private LocalDateTime lastActivity;
    private Map<String, Object> metadata;

    public ConversationState() {
        this.cartItems = new ArrayList<>();
        this.metadata = new HashMap<>();
        this.lastActivity = LocalDateTime.now();
    }

    public ConversationState(String sessionId) {
        this();
        this.sessionId = sessionId;
    }

    // Getters and Setters
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public List<CartItem> getCartItems() {
        return cartItems;
    }

    public void setCartItems(List<CartItem> cartItems) {
        this.cartItems = cartItems;
    }

    public String getTableNumber() {
        return tableNumber;
    }

    public void setTableNumber(String tableNumber) {
        this.tableNumber = tableNumber;
    }

    public LocalDateTime getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(LocalDateTime lastActivity) {
        this.lastActivity = lastActivity;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public void updateActivity() {
        this.lastActivity = LocalDateTime.now();
    }
}

