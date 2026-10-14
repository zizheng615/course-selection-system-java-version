package com.example.cs.repository;

import com.example.cs.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * 用 JpaSpecificationExecutor 组装条件查询，
 * 避免 JPQL 中 {@code :param IS NULL} 带来的参数类型推断问题。
 */
public interface StudentRepository extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {

    Optional<Student> findByUserId(Long userId);

    Optional<Student> findByStudentNo(String studentNo);

    boolean existsByStudentNo(String studentNo);

    /** 供前端筛选下拉框使用 */
    @Query("SELECT DISTINCT s.major FROM Student s ORDER BY s.major")
    List<String> findDistinctMajors();
}
