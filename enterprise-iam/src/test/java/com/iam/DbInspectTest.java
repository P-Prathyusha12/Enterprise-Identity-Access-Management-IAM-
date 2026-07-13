package com.iam;

import com.iam.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class DbInspectTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    public void testInspect() {
        System.out.println("========== TEST DATABASE INSPECT ==========");
        userRepository.findAll().forEach(user -> {
            System.out.println("USER IN DB: " + user.getUsername() + ", email=" + user.getEmail() 
                + ", enabled=" + user.isEnabled() + ", emailVerified=" + user.isEmailVerified());
        });
        System.out.println("===========================================");
    }
}
