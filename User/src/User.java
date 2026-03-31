abstract class User{
    protected String userId;
    protected String username;
    protected String passwordHash;
    protected String fullName;
    protected String email;
    protected String description;

    public User(String username, String passwordHash, String email, String fullName, UserRole userRole) {
    }

    public void setUserId(String userId){this.userId = userId;}
    public void setUsername(String username){this.username = username;}
    public void setPasswordHash(String passwordHash){this.passwordHash = passwordHash;}
    public void setFullName(String fullName){this.fullName = fullName;}
    public void setEmail(String email){this.email = email;}
    public void setDescription(String description){this.description = description;}

    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getDescription() { return description; }
    @Override
    public String toString(){
        return getUsername() + "(" + getUserId() + " , " + ")";
    }
}
