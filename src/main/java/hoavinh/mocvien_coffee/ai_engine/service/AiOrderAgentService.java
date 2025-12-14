package hoavinh.mocvien_coffee.ai_engine.service;

import hoavinh.mocvien_coffee.ai_engine.advisors.OrderIntent;
import hoavinh.mocvien_coffee.ai_engine.tools.AiOrderTools;
import hoavinh.mocvien_coffee.dto.AiOrderRequest;
import hoavinh.mocvien_coffee.dto.AiOrderResponse;
import hoavinh.mocvien_coffee.dto.CartItemDto;
import hoavinh.mocvien_coffee.dto.OrderItemRequest;
import hoavinh.mocvien_coffee.dto.OrderRequest;
import hoavinh.mocvien_coffee.model.Order;
import hoavinh.mocvien_coffee.model.User;
import hoavinh.mocvien_coffee.repository.UserRepository;
import hoavinh.mocvien_coffee.service.OrderService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agentic AI Order Service - LLM tự quyết định gọi tools
 */
@Service
public class AiOrderAgentService {

    private final ChatClient chatClient;
    private final ConversationService conversationService;
    private final OrderService orderService;
    private final UserRepository userRepository;
    private final AiOrderTools aiOrderTools;

    public AiOrderAgentService(
            ChatClient.Builder chatClientBuilder,
            ConversationService conversationService,
            OrderService orderService,
            UserRepository userRepository,
            AiOrderTools aiOrderTools) {
        
        this.conversationService = conversationService;
        this.orderService = orderService;
        this.userRepository = userRepository;
        this.aiOrderTools = aiOrderTools;

        // ChatClient cho LLM responses
        this.chatClient = chatClientBuilder
                .defaultSystem("""
                        Bạn là nhân viên AI thân thiện của quán Mộc Miên.
                        Bạn giúp khách hàng đặt món, trả lời câu hỏi về menu.
                        Luôn xưng 'em' và gọi khách là 'anh/chị'.
                        Trả lời ngắn gọn, vui vẻ, nhiệt tình.
                        """)
                .build();
    }

    /**
     * Xử lý message từ khách hàng - Agentic Flow
     * LLM tự quyết định gọi tools nào
     */
    public AiOrderResponse processCustomerMessage(AiOrderRequest request) {
        String message = request.message();
        String sessionId = request.sessionId();
        String tableNumber = request.tableNumber();

        // Get or create conversation
        ConversationState state = conversationService.getOrCreateConversation(sessionId);
        if (tableNumber != null && !tableNumber.isBlank()) {
            conversationService.setTableNumber(sessionId, tableNumber);
            state.setTableNumber(tableNumber);
        }

        // Build context cho LLM
        String context = buildContext(state);

        // Agentic Flow: Phân tích message và gọi tools phù hợp
        String aiMessage;
        
        // Simple heuristic để detect intent và gọi tools
        String lowerMessage = message.toLowerCase();
        
        if (lowerMessage.contains("giỏ hàng") || lowerMessage.contains("cart")) {
            // Gọi viewCart tool
            aiMessage = aiOrderTools.viewCart().apply(sessionId);
        } else if (lowerMessage.contains("xóa") || lowerMessage.contains("bỏ")) {
            // Extract item name và gọi removeFromCart
            String itemName = extractItemName(message);
            aiMessage = aiOrderTools.removeFromCart()
                    .apply(new hoavinh.mocvien_coffee.ai_engine.tools.AiOrderTools.RemoveFromCartRequest(itemName, sessionId));
        } else if (lowerMessage.matches(".*\\d+.*") && 
                   (lowerMessage.contains("cho") || lowerMessage.contains("đặt") || lowerMessage.contains("muốn"))) {
            // Có vẻ như muốn đặt món - extract và gọi addToCart
            String itemName = extractItemName(message);
            Integer quantity = extractQuantity(message);
            aiMessage = aiOrderTools.addToCart()
                    .apply(new hoavinh.mocvien_coffee.ai_engine.tools.AiOrderTools.AddToCartRequest(itemName, quantity, sessionId));
        } else if (lowerMessage.contains("menu") || lowerMessage.contains("món") || lowerMessage.contains("có gì")) {
            // Hỏi về menu - gọi getMenu
            aiMessage = aiOrderTools.getMenu().apply(message);
        } else {
            // Dùng LLM để trả lời chung
            String promptText = """
                    Context:
                    {context}
                    
                    Khách hàng nói: {message}
                    
                    Hãy trả lời một cách thân thiện, hỏi xem khách muốn gì.
                    """;

            PromptTemplate template = new PromptTemplate(promptText);
            Prompt prompt = template.create(Map.of(
                    "context", context,
                    "message", message
            ));

            aiMessage = chatClient.prompt(prompt)
                    .call()
                    .content();
        }

        // Detect intent từ response (có thể cải thiện)
        OrderIntent intent = detectIntentFromResponse(aiMessage, state);

        // Check nếu có confirm order
        if (isConfirmOrder(message, aiMessage)) {
            return handleConfirmOrder(state);
        }

        // Build response
        return buildResponse(aiMessage, intent, state, false);
    }

    /**
     * Xử lý confirm order
     */
    private AiOrderResponse handleConfirmOrder(ConversationState state) {
        List<CartItem> cartItems = conversationService.getCart(state.getSessionId());
        
        if (cartItems.isEmpty()) {
            String response = "Giỏ hàng của anh/chị đang trống. Anh/chị muốn xem menu không ạ?";
            return buildResponse(response, OrderIntent.CONFIRM_ORDER, state, false);
        }

        // Convert to OrderRequest
        List<OrderItemRequest> orderItems = cartItems.stream()
                .map(item -> new OrderItemRequest(
                        item.getProduct().getId(),
                        item.getQuantity(),
                        item.getPrice(),
                        item.getNote()
                ))
                .collect(Collectors.toList());

        String tableNumber = state.getTableNumber() != null ? 
                state.getTableNumber() : "Takeout";

        OrderRequest orderRequest = new OrderRequest(
                null,
                tableNumber,
                null,
                null,
                null,
                null,
                null,
                null,
                orderItems
        );

        try {
            User aiUser = userRepository.findByUsername("admin")
                    .orElse(userRepository.findAll().stream().findFirst().orElseThrow());

            Order order = orderService.createOrder(orderRequest, aiUser);
            conversationService.clearConversation(state.getSessionId());

            String response = String.format(
                    "✅ Đặt hàng thành công!\n" +
                    "Mã đơn: #%d\n" +
                    "Tổng tiền: %s VNĐ\n" +
                    "Cảm ơn anh/chị đã đặt hàng tại quán Mộc Miên!",
                    order.getId(),
                    formatPrice(order.getTotalAmount())
            );

            return new AiOrderResponse(
                    response,
                    OrderIntent.CONFIRM_ORDER,
                    new ArrayList<>(),
                    order.getTotalAmount(),
                    order.getId(),
                    false
            );
        } catch (Exception e) {
            String response = "Xin lỗi, có lỗi xảy ra khi đặt hàng. Anh/chị vui lòng thử lại ạ.";
            return buildResponse(response, OrderIntent.CONFIRM_ORDER, state, false);
        }
    }

    /**
     * Build context cho LLM
     */
    private String buildContext(ConversationState state) {
        StringBuilder context = new StringBuilder();
        
        if (state.getTableNumber() != null) {
            context.append("Bàn: ").append(state.getTableNumber()).append("\n");
        }

        List<CartItem> cartItems = state.getCartItems();
        if (!cartItems.isEmpty()) {
            context.append("Giỏ hàng hiện tại:\n");
            for (CartItem item : cartItems) {
                context.append(String.format("- %s x%d (%s VNĐ)\n",
                        item.getProduct().getName(),
                        item.getQuantity(),
                        formatPrice(item.getSubtotal())));
            }
            context.append("Tổng: ").append(formatPrice(conversationService.getCartTotal(state.getSessionId())))
                    .append(" VNĐ\n");
        } else {
            context.append("Giỏ hàng đang trống.\n");
        }

        return context.toString();
    }

    /**
     * Detect intent từ response (simple heuristic)
     */
    private OrderIntent detectIntentFromResponse(String response, ConversationState state) {
        String lower = response.toLowerCase();
        
        if (lower.contains("đã thêm") || lower.contains("thêm vào giỏ")) {
            return OrderIntent.ADD_TO_CART;
        }
        if (lower.contains("giỏ hàng") && state.getCartItems().isEmpty()) {
            return OrderIntent.VIEW_CART;
        }
        if (lower.contains("xóa") || lower.contains("bỏ")) {
            return OrderIntent.REMOVE_FROM_CART;
        }
        if (lower.contains("menu") || lower.contains("món")) {
            return OrderIntent.ASK_MENU;
        }
        
        return OrderIntent.UNKNOWN;
    }

    /**
     * Check nếu user muốn confirm order
     */
    private boolean isConfirmOrder(String userMessage, String aiResponse) {
        String lower = userMessage.toLowerCase();
        return lower.contains("đặt hàng") ||
               lower.contains("xác nhận") ||
               lower.contains("thanh toán") ||
               lower.contains("checkout") ||
               lower.contains("ok") && lower.contains("đặt");
    }

    /**
     * Build response
     */
    private AiOrderResponse buildResponse(
            String message,
            OrderIntent intent,
            ConversationState state,
            boolean requiresConfirmation) {
        
        List<CartItemDto> cartItems = state.getCartItems().stream()
                .map(item -> new CartItemDto(
                        item.getProduct().getId(),
                        item.getProduct().getName(),
                        item.getQuantity(),
                        item.getPrice(),
                        item.getNote()
                ))
                .collect(Collectors.toList());

        Double totalAmount = conversationService.getCartTotal(state.getSessionId());

        return new AiOrderResponse(
                message,
                intent,
                cartItems,
                totalAmount,
                null,
                requiresConfirmation
        );
    }

    /**
     * Format price
     */
    private String formatPrice(Double price) {
        if (price == null) return "0";
        return String.format("%.0f", price);
    }

    /**
     * Extract item name from message
     */
    private String extractItemName(String message) {
        // Simple extraction - có thể cải thiện
        String[] parts = message.split("\\s+");
        StringBuilder itemName = new StringBuilder();
        boolean found = false;
        for (String part : parts) {
            if (part.matches("\\d+")) {
                found = true;
                continue;
            }
            if (found && !part.equalsIgnoreCase("cho") && !part.equalsIgnoreCase("tôi") 
                    && !part.equalsIgnoreCase("đặt") && !part.equalsIgnoreCase("muốn")) {
                itemName.append(part).append(" ");
            }
        }
        return itemName.toString().trim();
    }

    /**
     * Extract quantity from message
     */
    private Integer extractQuantity(String message) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return 1;
            }
        }
        return 1;
    }
}

