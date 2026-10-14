package com.example.cs.service;

import com.example.cs.repository.StatsRepository;
import com.example.cs.vo.MajorStatVO;
import com.example.cs.vo.PopularCourseVO;
import com.example.cs.vo.TeacherWorkloadVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 统计报表服务。三条查询分别对应：
 * 聚合排行、多维分组、外部连接补全统计口径。
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final StatsRepository statsRepo;
    private final SemesterResolver semesterResolver;

    /** 热门课程 Top-N（含满座率） */
    @Transactional(readOnly = true)
    public List<PopularCourseVO> popularCourses(String semester, int limit) {
        String sem = semesterResolver.resolve(semester);
        int top = (limit <= 0 || limit > 100) ? 10 : limit;
        return statsRepo.findPopularCourses(sem, top).stream().map(PopularCourseVO::of).toList();
    }

    /** 各专业选课分布 */
    @Transactional(readOnly = true)
    public List<MajorStatVO> majorDistribution() {
        return statsRepo.findMajorDistribution().stream().map(MajorStatVO::of).toList();
    }

    /** 教师工作量 */
    @Transactional(readOnly = true)
    public List<TeacherWorkloadVO> teacherWorkload(String semester) {
        String sem = semesterResolver.resolve(semester);
        return statsRepo.findTeacherWorkload(sem).stream().map(TeacherWorkloadVO::of).toList();
    }
}
