package hoavinh.mocvien_coffee.ai_engine.advisors;

public enum OrderIntent {
    ASK_MENU,           // Hỏi về menu
    ADD_TO_CART,        // Thêm vào giỏ
    VIEW_CART,          // Xem giỏ hàng
    REMOVE_FROM_CART,   // Xóa khỏi giỏ
    UPDATE_QUANTITY,    // Cập nhật số lượng
    CONFIRM_ORDER,      // Xác nhận order
    CANCEL,             // Hủy
    GREETING,           // Chào hỏi
    UNKNOWN             // Không xác định
}

