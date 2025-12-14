package hoavinh.mocvien_coffee.ai_engine.service;

import hoavinh.mocvien_coffee.model.Product;

public class CartItem {
    private Product product;
    private int quantity;
    private String note;
    private Double price; // snapshot price at time of adding

    public CartItem() {
    }

    public CartItem(Product product, int quantity, String note, Double price) {
        this.product = product;
        this.quantity = quantity;
        this.note = note;
        this.price = price;
    }

    // Getters and Setters
    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Double getSubtotal() {
        return price * quantity;
    }
}

