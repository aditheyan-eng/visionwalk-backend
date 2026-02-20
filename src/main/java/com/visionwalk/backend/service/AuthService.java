package com.visionwalk.backend.service;

import com.visionwalk.backend.model.User;
import com.visionwalk.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Authenticates a user by checking email and password against the database.
     */
    public User authenticate(String email, String password) throws Exception {
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // Check if the provided password matches the stored password
            if (user.getPassword() != null && user.getPassword().equals(password)) {
                return user;
            } else {
                throw new Exception("Invalid password.");
            }
        } else {
            throw new Exception("User not found with email: " + email);
        }
    }

    /**
     * Registers a new user in the database.
     */
    public User registerUser(String name, String email, String password) throws Exception {
        // Check if email is already in use
        if (userRepository.findByEmail(email).isPresent()) {
            throw new Exception("Email already registered!");
        }

        User newUser = new User();
        newUser.setName(name);
        newUser.setEmail(email);
        newUser.setPassword(password); // Storing as plain text for now

        return userRepository.save(newUser);
    }
}