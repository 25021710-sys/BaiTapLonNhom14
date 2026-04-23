package com.auction.server.util;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;


public class PasswordUtil {
    private PasswordUtil(){
    }
    public static String generationSalt(){
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }
    public static String hash(String password, String salt){
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String salted = salt + password;
            byte[] hashed = md.digest(salted.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e){
            throw new RuntimeException("SHA-256 không khả dụng", e);
        }
    }
    public static boolean verify(String password,String salt,String storeHash){
        return hash(password, salt).equals(storeHash);
    }
}
