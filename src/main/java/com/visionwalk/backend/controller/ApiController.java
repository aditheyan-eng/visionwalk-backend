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

            // 2. Build the request for Google Cloud AI
            ByteString imgBytes = ByteString.copyFrom(imageBytes);
            Image img = Image.newBuilder().setContent(imgBytes).build();
            
            // We want LABEL_DETECTION (identifies objects, environments, surfaces)
            Feature feat = Feature.newBuilder().setType(Feature.Type.LABEL_DETECTION).setMaxResults(10).build();
            AnnotateImageRequest request = AnnotateImageRequest.newBuilder().addFeatures(feat).setImage(img).build();
            List<AnnotateImageRequest> requests = new ArrayList<>();
            requests.add(request);

            // 3. Send to Google's Supercomputers
            List<String> detectedLabels = new ArrayList<>();
            
            try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
                BatchAnnotateImagesResponse response = client.batchAnnotateImages(requests);
                List<AnnotateImageResponse> responses = response.getResponsesList();

                for (AnnotateImageResponse res : responses) {
                    if (res.hasError()) {
                        System.err.println("Google AI Error: " + res.getError().getMessage());
                        return ResponseEntity.status(500).body(Map.of("error", res.getError().getMessage()));
                    }
                    
                    // Extract the text of everything it saw!
                    for (EntityAnnotation annotation : res.getLabelAnnotationsList()) {
                        detectedLabels.add(annotation.getDescription().toLowerCase());
                    }
                }
            }

            // 4. Send the list of objects back to React
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("labels", detectedLabels);
            
            System.out.println("Cloud AI Saw: " + detectedLabels);
            return ResponseEntity.ok(responseMap);
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Vision API failed to process image"));
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