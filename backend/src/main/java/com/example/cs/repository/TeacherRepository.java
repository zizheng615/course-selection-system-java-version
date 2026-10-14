package com.example.cs.repository;

import com.example.cs.entity.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, Long>, JpaSpecificationExecutor<Teacher> {

    Optional<Teacher> findByUserId(Long userId);

    boolean existsByTeacherNo(String teacherNo);
}
