abstract class User {
    protected String userId;
    protected String username;
    protected String passwordHash;
    protected String fullName;
    protected String email;
    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
}
