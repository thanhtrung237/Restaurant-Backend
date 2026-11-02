package com.group.restaurant.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utility để hash password bằng BCrypt từ command line.
 * 
 * Usage: ./gradlew hashPassword -Ppassword=yourpassword
 */
public class PasswordHasher {
    
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("❌ Error: No password provided");
            System.err.println("\nUsage:");
            System.err.println("  ./gradlew hashPassword -Ppassword=yourpassword");
            System.err.println("\nExample:");
            System.err.println("  ./gradlew hashPassword -Ppassword=123456789");
            System.exit(1);
        }
        
        String password = args[0];
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode(password);
        
        System.out.println("\n=== BCrypt Password Hash ===");
        System.out.println("\nPassword: " + password);
        System.out.println("\nBCrypt Hash:");
        System.out.println(hash);
        System.out.println("\n✅ Copy the hash above and use it in your SQL UPDATE statement");
        System.out.println("\nExample SQL:");
        System.out.println("UPDATE account SET password_hash = '" + hash + "' WHERE username = 'youruser';");
        System.out.println();
    }
}
