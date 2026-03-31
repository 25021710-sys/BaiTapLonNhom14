public enum ItemCategory {
    ELECTRONICS("Điện tử"),
    ART("Nghệ thuật"),
    VEHICLE("Phương tiện"),
    JEWELRY("Trang sức"),
    OTHER("Khác");

    private final String display;
    ItemCategory(String display) { this.display = display; }
    public String getDisplay() { return display; }

    @Override
    public String toString() { return display; }
}
