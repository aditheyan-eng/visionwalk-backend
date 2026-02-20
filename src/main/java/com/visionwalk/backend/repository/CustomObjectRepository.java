package com.visionwalk.backend.repository;

import com.visionwalk.backend.model.CustomObject;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CustomObjectRepository extends JpaRepository<CustomObject, Long> {
    List<CustomObject> findByUserId(Long userId);
}