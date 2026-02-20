package com.visionwalk.backend.controller;

import com.visionwalk.backend.model.*;
import com.visionwalk.backend.repository.*;
import com.visionwalk.backend.service.EmailService;
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
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class ApiController {
	@Autowired 
	private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private CustomObjectRepository objectRepository;
    @Autowired private EmailService emailService;

    // --- 1. SIGNUP (New) ---
 // --- 1. SIGNUP ---
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody Map<String, String> payload) {
        try {
            // We call registerUser instead of verifyGoogleToken
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
        try {
            // We call authenticate instead of verifyGoogleToken
            User user = authService.authenticate(
                payload.get("email"),
                payload.get("password")
            );
            System.out.println("✅ Login successful for: " + user.getEmail());
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            System.out.println("❌ LOGIN FAILED: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
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

    // --- 4. EMERGENCY ALERT ---
    @PostMapping("/alert")
    public ResponseEntity<?> sendAlert(@RequestBody Map<String, Object> payload) {
        Long userId = Long.parseLong(payload.get("userId").toString());
        double lat = Double.parseDouble(payload.get("lat").toString());
        double lng = Double.parseDouble(payload.get("lng").toString());

        User user = userRepository.findById(userId).orElseThrow();
        if (user.getGuardianEmail() != null && !user.getGuardianEmail().isEmpty()) {
            emailService.sendEmergencyAlert(user.getGuardianEmail(), user.getName(), lat, lng);
            return ResponseEntity.ok("Alert Sent");
        }
        return ResponseEntity.badRequest().body("Guardian Email not set.");
    }

    // --- 5. SAVED LOCATIONS ---
    @PostMapping("/locations")
    public ResponseEntity<?> saveLocation(@RequestBody Map<String, Object> payload) {
        Long userId = Long.parseLong(payload.get("userId").toString());
        User user = userRepository.findById(userId).orElseThrow();

        SavedLocation loc = new SavedLocation();
        loc.setName((String) payload.get("name"));
        loc.setLatitude(Double.parseDouble(payload.get("latitude").toString()));
        loc.setLongitude(Double.parseDouble(payload.get("longitude").toString()));
        loc.setUser(user);

        return ResponseEntity.ok(locationRepository.save(loc));
    }

    @GetMapping("/locations/search")
    public ResponseEntity<?> searchLocation(@RequestParam Long userId, @RequestParam String name) {
        Optional<SavedLocation> loc = locationRepository.findByUserIdAndNameIgnoreCase(userId, name);
        return loc.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/locations")
    public ResponseEntity<List<SavedLocation>> getAllLocations(@RequestParam Long userId) {
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