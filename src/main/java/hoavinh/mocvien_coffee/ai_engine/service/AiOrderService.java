package hoavinh.mocvien_coffee.ai_engine.service;

import hoavinh.mocvien_coffee.ai_engine.advisors.OrderIntent;
import hoavinh.mocvien_coffee.ai_engine.advisors.OrderIntentAdvisor;
import hoavinh.mocvien_coffee.ai_engine.tools.OrderExtractionHelper;
import hoavinh.mocvien_coffee.dto.AiOrderRequest;
import hoavinh.mocvien_coffee.dto.AiOrderResponse;
import hoavinh.mocvien_coffee.dto.CartItemDto;
import hoavinh.mocvien_coffee.dto.OrderItemRequest;
import hoavinh.mocvien_coffee.dto.OrderRequest;
import hoavinh.mocvien_coffee.model.Order;
import hoavinh.mocvien_coffee.model.Product;
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

@Service
public class AiOrderService {

    private final ChatClient chatClient;
    private final MenuContextService menuContextService;
    private final ProductMatchingService productMatchingService;
    private final ConversationService conversationService;
    private final OrderIntentAdvisor intentAdvisor;
    private final OrderService orderService;
    private final UserRepository userRepository;

    public AiOrderService(
            ChatClient.Builder chatClientBuilder,
            MenuContextService menuContextService,
            ProductMatchingService productMatchingService,
            ConversationService conversationService,
            OrderIntentAdvisor intentAdvisor,
            OrderService orderService,
            UserRepository userRepository) {
        this.chatClient = chatClientBuilder
                .defaultSystem("Bạn là nhân viên AI thân thiện của quán Mộc Miên. " +
                        "Bạn giúp khách hàng đặt món, trả lời câu hỏi về menu. " +
                        "Luôn xưng 'em' và gọi khách là 'anh/chị'. " +
                        "Trả lời ngắn gọn, vui vẻ, nhiệt tình.")
                .build();
        this.menuContextService = menuContextService;
        this.productMatchingService = productMatchingService;
        this.conversationService = conversationService;
        this.intentAdvisor = intentAdvisor;
        this.orderService = orderService;
        this.userRepository = userRepository;
    }

    /**
     * Xử lý message từ khách hàng
     */
    public AiOrderResponse processCustomerMessage(AiOrderRequest request) {
        String message = request.message();
        String sessionId = request.sessionId();
        String tableNumber = request.tableNumber();

        // Load menu
        List<Product> menu = menuContextService.loadMenuFromDatabase();
        String menuContext = menuContextService.formatMenuForAiContext(menu);

        // Get or create conversation
        ConversationState state = conversationService.getOrCreateConversation(sessionId);
        if (tableNumber != null && !tableNumber.isBlank()) {
            conversationService.setTableNumber(sessionId, tableNumber);
            state.setTableNumber(tableNumber);
        }

        // Detect intent
        OrderIntent intent = intentAdvisor.detectIntent(message, state);

        // Handle intent
        return handleIntent(intent, message, state, menu, menuContext);
    }

    /**
     * Xử lý theo intent
     */
    private AiOrderResponse handleIntent(
            OrderIntent intent,
            String message,
            ConversationState state,
            List<Product> menu,
            String menuContext) {

        return switch (intent) {
            case GREETING -> handleGreeting(state, menuContext);
            case ASK_MENU -> handleAskMenu(message, menuContext);
            case ADD_TO_CART -> handleAddToCart(message, state, menu);
            case VIEW_CART -> handleViewCart(state);
            case REMOVE_FROM_CART -> handleRemoveFromCart(message, state);
            case UPDATE_QUANTITY -> handleUpdateQuantity(message, state);
            case CONFIRM_ORDER -> handleConfirmOrder(state);
            case CANCEL -> handleCancel(state);
            default -> handleUnknown(message, menuContext);
        };
    }

    /**
     * Xử lý greeting
     */
    private AiOrderResponse handleGreeting(ConversationState state, String menuContext) {
        String response = "Xin chào anh/chị! Em là nhân viên AI của quán Mộc Miên. " +
                "Em có thể giúp anh/chị xem menu, đặt món hoặc trả lời câu hỏi. " +
                "Anh/chị cần gì ạ?";

        return buildResponse(response, OrderIntent.GREETING, state, false);
    }

    /**
     * Xử lý câu hỏi về menu
     */
    private AiOrderResponse handleAskMenu(String message, String menuContext) {
        String promptText = """
                Dựa vào menu sau:
                {menuContext}
                ----------------------
                Trả lời khách hàng câu hỏi: {question}
                (Trả lời ngắn gọn, vui vẻ, xưng là 'em' và gọi khách là 'anh/chị')
                """;

        PromptTemplate template = new PromptTemplate(promptText);
        Prompt prompt = template.create(Map.of(
                "menuContext", menuContext,
                "question", message
        ));

        String aiResponse = chatClient.prompt(prompt)
                .call()
                .content();

        return new AiOrderResponse(
                aiResponse,
                OrderIntent.ASK_MENU,
                new ArrayList<>(),
                null,
                null,
                false
        );
    }

    /**
     * Xử lý thêm vào giỏ hàng
     */
    private AiOrderResponse handleAddToCart(String message, ConversationState state, List<Product> menu) {
        // Extract order items
        List<OrderExtractionHelper.ExtractedItem> extractedItems = 
                OrderExtractionHelper.extractOrderItems(message, menu);

        if (extractedItems.isEmpty()) {
            // Nếu không extract được, dùng AI để hỏi lại
            String response = "Em chưa hiểu rõ anh/chị muốn đặt món gì. " +
                    "Anh/chị có thể nói rõ tên món và số lượng không ạ? " +
                    "Ví dụ: 'cho tôi 2 cà phê đen'";
            return buildResponse(response, OrderIntent.ADD_TO_CART, state, false);
        }

        // Convert to CartItem
        List<CartItem> cartItems = extractedItems.stream()
                .map(item -> new CartItem(
                        item.getProduct(),
                        item.getQuantity(),
                        item.getNote(),
                        item.getProduct().getPrice()
                ))
                .collect(Collectors.toList());

        // Validate
        ValidationResult validation = validateCartItems(cartItems);
        if (!validation.isValid()) {
            return buildResponse(validation.getMessage(), OrderIntent.ADD_TO_CART, state, false);
        }

        // Add to cart
        conversationService.addToCart(state.getSessionId(), cartItems);

        // Generate response
        StringBuilder response = new StringBuilder("Em đã thêm vào giỏ hàng:\n");
        for (CartItem item : cartItems) {
            response.append(String.format("• %s x%d - %s VNĐ\n",
                    item.getProduct().getName(),
                    item.getQuantity(),
                    formatPrice(item.getSubtotal())));
        }
        response.append("\nAnh/chị muốn thêm món nữa không ạ? Hoặc nói 'xem giỏ hàng' để kiểm tra.");

        return buildResponse(response.toString(), OrderIntent.ADD_TO_CART, state, false);
    }

    /**
     * Xử lý xem giỏ hàng
     */
    private AiOrderResponse handleViewCart(ConversationState state) {
        String cartSummary = conversationService.getCartSummary(state.getSessionId());
        String response = cartSummary + "\n\nAnh/chị muốn đặt hàng không ạ? Nói 'đặt hàng' hoặc 'xác nhận' để hoàn tất.";

        return buildResponse(response, OrderIntent.VIEW_CART, state, false);
    }

    /**
     * Xử lý xóa khỏi giỏ hàng
     */
    private AiOrderResponse handleRemoveFromCart(String message, ConversationState state) {
        // Extract product names to remove
        List<String> productNames = extractProductNamesFromMessage(message, state);
        
        if (productNames.isEmpty()) {
            String response = "Em chưa hiểu anh/chị muốn xóa món gì. " +
                    "Anh/chị có thể nói rõ tên món không ạ?";
            return buildResponse(response, OrderIntent.REMOVE_FROM_CART, state, false);
        }

        conversationService.removeFromCart(state.getSessionId(), productNames);
        String response = "Em đã xóa món khỏi giỏ hàng. " +
                "Anh/chị muốn xem lại giỏ hàng không ạ?";

        return buildResponse(response, OrderIntent.REMOVE_FROM_CART, state, false);
    }

    /**
     * Xử lý cập nhật số lượng
     */
    private AiOrderResponse handleUpdateQuantity(String message, ConversationState state) {
        // TODO: Implement quantity update logic
        String response = "Tính năng cập nhật số lượng đang được phát triển. " +
                "Anh/chị có thể xóa món và thêm lại với số lượng mới ạ.";
        return buildResponse(response, OrderIntent.UPDATE_QUANTITY, state, false);
    }

    /**
     * Xử lý xác nhận order
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
                null, // tableId
                tableNumber,
                null, // customerId
                null, // customerName
                null, // customerPhoneNumber
                null, // totalAmount - sẽ tính tự động
                null, // surchargePercent
                null, // surchargeAmount
                null, // surchargeName
                null, // customerCash
                null, // changeAmount
                orderItems
        );

        try {
            // Get default user (hoặc có thể tạo user AI)
            User aiUser = userRepository.findByUsername("admin")
                    .orElse(userRepository.findAll().stream().findFirst().orElseThrow());

            Order order = orderService.createOrder(orderRequest, aiUser);
            
            // Clear cart
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
     * Xử lý hủy
     */
    private AiOrderResponse handleCancel(ConversationState state) {
        conversationService.clearConversation(state.getSessionId());
        String response = "Em đã hủy đơn hàng. Anh/chị cần gì nữa không ạ?";
        return buildResponse(response, OrderIntent.CANCEL, state, false);
    }

    /**
     * Xử lý unknown intent
     */
    private AiOrderResponse handleUnknown(String message, String menuContext) {
        String promptText = """
                Dựa vào menu sau:
                {menuContext}
                ----------------------
                Khách hàng nói: {message}
                Hãy trả lời một cách thân thiện, hỏi xem khách muốn gì.
                (Trả lời ngắn gọn, xưng 'em' và gọi khách 'anh/chị')
                """;

        PromptTemplate template = new PromptTemplate(promptText);
        Prompt prompt = template.create(Map.of(
                "menuContext", menuContext,
                "message", message
        ));

        String aiResponse = chatClient.prompt(prompt)
                .call()
                .content();

        return new AiOrderResponse(
                aiResponse,
                OrderIntent.UNKNOWN,
                new ArrayList<>(),
                null,
                null,
                false
        );
    }

    /**
     * Validate cart items
     */
    private ValidationResult validateCartItems(List<CartItem> items) {
        if (items == null || items.isEmpty()) {
            return new ValidationResult(false, "Không có món nào để thêm vào giỏ hàng.");
        }

        for (CartItem item : items) {
            if (!item.getProduct().isAvailable()) {
                return new ValidationResult(false, 
                        String.format("Món '%s' hiện đang hết hàng ạ.", item.getProduct().getName()));
            }
            if (item.getQuantity() <= 0) {
                return new ValidationResult(false, "Số lượng phải lớn hơn 0.");
            }
        }

        return new ValidationResult(true, null);
    }

    /**
     * Extract product names from message để xóa
     */
    private List<String> extractProductNamesFromMessage(String message, ConversationState state) {
        List<String> productNames = new ArrayList<>();
        List<CartItem> cartItems = state.getCartItems();
        
        for (CartItem item : cartItems) {
            if (message.toLowerCase().contains(item.getProduct().getName().toLowerCase())) {
                productNames.add(item.getProduct().getName());
            }
        }
        
        return productNames;
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
     * Validation result
     */
    private static class ValidationResult {
        private final boolean valid;
        private final String message;

        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }
    }
}

