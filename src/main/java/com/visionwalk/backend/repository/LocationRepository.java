package com.visionwalk.backend.repository;

import com.visionwalk.backend.model.SavedLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<SavedLocation, Long> {
    List<SavedLocation> findByUserId(Long userId);
    // Finds location by name (Case Insensitive) for Voice Commands
    Optional<SavedLocation> findByUserIdAndNameIgnoreCase(Long userId, String name);
}