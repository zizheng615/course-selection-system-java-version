package com.example.cs.service;

import com.example.cs.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 学期解析：前端未传学期时，默认取库中最近的学期。
 * 独立成组件，避免 CourseService 与 EnrollmentService 互相依赖。
 */
@Component
@RequiredArgsConstructor
public class SemesterResolver {

    /** 库中还没有任何课程时的兜底值 */
    private static final String FALLBACK = "2026-2027-1";

    private final CourseRepository courseRepo;

    public String resolve(String semester) {
        if (StringUtils.hasText(semester)) {
            return semester.trim();
        }
        return courseRepo.findDistinctSemesters().stream().findFirst().orElse(FALLBACK);
    }
}
