public enum ItemCondition {
    NEW("Mới"), LIKE_NEW("Như mới"), GOOD("Tốt"), FAIR("Khá"), POOR("Kém");

    private final String display;
    ItemCondition(String display) { this.display = display; }
    public String getDisplay() { return display; }

    @Override
    public String toString() { return display; }
}