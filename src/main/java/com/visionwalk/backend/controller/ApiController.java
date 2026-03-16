package com.visionwalk.backend.controller;

import com.visionwalk.backend.model.*;

import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.visionwalk.backend.repository.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.visionwalk.backend.service.AuthService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "https://visionwalk.vercel.app", allowCredentials = "true")
public class ApiController {
    @Autowired 
    private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private CustomObjectRepository objectRepository;
   

    // --- 1. SIGNUP ---
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody Map<String, String> payload) {
        try {
            User user = authService.registerUser(
                payload.get("name"),
                payload.get("email"),
                payload.get("password")
            );
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

 // --- 2. LOGIN ---
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String password = payload.get("password");

        // 🚨 SECURE BACKEND ADMIN CHECK 🚨
        // Change the email and password here to whatever you want your admin credentials to be!
        if ("admin@visionwalk.com".equals(email) && "admin123".equals(password)) {
            System.out.println("🛡️ Admin Logged In!");
            
            // Create a custom response specifically for the Admin
            Map<String, Object> adminResponse = new java.util.HashMap<>();
            adminResponse.put("id", 0); // Special ID for Admin
            adminResponse.put("name", "Administrator");
            adminResponse.put("email", "admin@visionwalk.com");
            adminResponse.put("role", "ADMIN"); // React looks for this flag to redirect to /admin
            
            return ResponseEntity.ok(adminResponse);
        }

        
        // --- NORMAL USER LOGIN FLOW ---
        try {
            User user = authService.authenticate(email, password);
            System.out.println("✅ Login successful for: " + user.getEmail());
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            System.out.println("❌ LOGIN FAILED: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }
 // ==========================================
    // CLOUD VISION API (ENVIRONMENT SCANNER)
 // ==========================================
    // FREE CLOUD NARRATOR (HUGGING FACE API)
    // ==========================================
    @PostMapping("/vision/analyze")
    public ResponseEntity<?> analyzeEnvironment(@RequestBody Map<String, String> payload) {
        String base64Image = payload.get("image");
        
        if (base64Image == null || base64Image.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No image provided"));
        }

        try {
            // 1. Strip the HTML canvas prefix to get pure base64 data
            String[] parts = base64Image.split(",");
            String imageString = parts.length > 1 ? parts[1] : parts[0];
            byte[] imageBytes = java.util.Base64.getDecoder().decode(imageString);

            // 2. Setup the free Hugging Face API connection
            // We are using the BLIP Image Captioning model
            String hfToken = System.getenv("HF_TOKEN"); // We will set this in Render!
            if (hfToken == null) {
                return ResponseEntity.status(500).body(Map.of("error", "API Token missing"));
            }

            java.net.URL url = new java.net.URL("https://api-inference.huggingface.co/models/Salesforce/blip-image-captioning-base");
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Authorization", "Bearer " + hfToken);
            con.setRequestProperty("Content-Type", "application/octet-stream");
            con.setDoOutput(true);

            // 3. Send the image to Hugging Face
            try (java.io.OutputStream os = con.getOutputStream()) {
                os.write(imageBytes);
            }

            // 4. Read the AI's response
            java.io.InputStream inputStream = con.getResponseCode() >= 400 ? con.getErrorStream() : con.getInputStream();
            String responseStr = new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

            // 5. Hugging Face returns JSON like: [{"generated_text": "a flight of stairs and a wall"}]
            // We extract the sentence and send it back to React in the "labels" array
            List<String> detectedLabels = new ArrayList<>();
            
            // Simple string extraction to avoid importing massive JSON libraries
            if (responseStr.contains("generated_text")) {
                String caption = responseStr.split("\"generated_text\":\"")[1].split("\"")[0];
                detectedLabels.add(caption.toLowerCase());
                System.out.println("Hugging Face Saw: " + caption);
            } else {
                detectedLabels.add("clear path");
            }

            // Send back to React
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("labels", detectedLabels);
            
            return ResponseEntity.ok(responseMap);
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Free Vision API failed"));
        }
    }
    
// 🚨 Add this inside your ApiController 🚨
    
    @DeleteMapping("/locations/{id}") // Ensure this matches your API path structure
    public ResponseEntity<?> deleteLocation(@PathVariable Long id) {
        try {
            // Replace 'locationRepository' with whatever you named your database interface!
            locationRepository.deleteById(id);
            
            return ResponseEntity.ok().body("Location deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error deleting location: " + e.getMessage());
        }
    }
    // --- 3. GUARDIAN ---
    @PostMapping("/guardian")
    public ResponseEntity<?> setGuardian(@RequestBody Map<String, String> payload) {
        Long userId = Long.parseLong(payload.get("userId"));
        User user = userRepository.findById(userId).orElseThrow();
        user.setGuardianEmail(payload.get("guardianEmail"));
        return ResponseEntity.ok(userRepository.save(user));
    }
 // ==========================================
    // ADMIN ROUTES
    // ==========================================
    
    // Get all users
    @GetMapping("/admin/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    // Delete a user by ID
    @DeleteMapping("/admin/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            userRepository.deleteById(id);
            return ResponseEntity.ok().body(Map.of("message", "User deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to delete user");
        }
    }
 

    // --- 5. SAVED LOCATIONS ---
    @PostMapping("/locations")
    public ResponseEntity<?> saveLocation(@RequestBody Map<String, Object> payload) {
        Long userId = Long.parseLong(payload.get("userId").toString());
        User user = userRepository.findById(userId).orElseThrow();

        SavedLocation loc = new SavedLocation();
        loc.setName((String) payload.get("name"));
        
        // MATCHED WITH REACT: Using "lat" and "lng" instead of "latitude" and "longitude"
        loc.setLatitude(Double.parseDouble(payload.get("lat").toString()));
        loc.setLongitude(Double.parseDouble(payload.get("lng").toString()));
        loc.setUser(user);

        return ResponseEntity.ok(locationRepository.save(loc));
    }

    @GetMapping("/locations/search")
    public ResponseEntity<?> searchLocation(@RequestParam Long userId, @RequestParam String name) {
        Optional<SavedLocation> loc = locationRepository.findByUserIdAndNameIgnoreCase(userId, name);
        return loc.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    // MATCHED WITH REACT: Changed to @PathVariable so it correctly reads /api/locations/{userId}
    @GetMapping("/locations/{userId}")
    public ResponseEntity<List<SavedLocation>> getAllLocations(@PathVariable Long userId) {
        return ResponseEntity.ok(locationRepository.findByUserId(userId));
    }

    // --- 6. CUSTOM OBJECTS ---
    @PostMapping("/objects")
    public ResponseEntity<?> addObject(@RequestBody Map<String, String> payload) {
        Long userId = Long.parseLong(payload.get("userId"));
        User user = userRepository.findById(userId).orElseThrow();

        CustomObject obj = new CustomObject();
        obj.setObjectName(payload.get("objectName"));
        obj.setDescription(payload.get("description"));
        obj.setUser(user);

        return ResponseEntity.ok(objectRepository.save(obj));
    }

    @GetMapping("/objects")
    public ResponseEntity<List<CustomObject>> getObjects(@RequestParam Long userId) {
        return ResponseEntity.ok(objectRepository.findByUserId(userId));
    }
}