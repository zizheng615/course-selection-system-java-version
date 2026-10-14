package com.example.cs.service;

import com.example.cs.common.BusinessException;
import com.example.cs.common.ErrorCode;
import com.example.cs.common.PageResult;
import com.example.cs.dto.TeacherDTO;
import com.example.cs.entity.SysUser;
import com.example.cs.entity.Teacher;
import com.example.cs.repository.CourseRepository;
import com.example.cs.repository.SysUserRepository;
import com.example.cs.repository.TeacherRepository;
import com.example.cs.vo.TeacherVO;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TeacherService {

    private static final String DEFAULT_PASSWORD = "123456";

    private final TeacherRepository teacherRepo;
    private final SysUserRepository userRepo;
    private final CourseRepository courseRepo;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public PageResult<TeacherVO> page(String keyword, String dept, int page, int size) {
        Specification<Teacher> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("name"), like),
                        cb.like(root.get("teacherNo"), like)));
            }
            if (StringUtils.hasText(dept)) {
                predicates.add(cb.equal(root.get("dept"), dept.trim()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Teacher> result = teacherRepo.findAll(spec,
                PageRequest.of(Math.max(page - 1, 0), size, Sort.by("id")));
        return PageResult.of(result, TeacherVO::of);
    }

    /** 供课程管理页的"授课教师"下拉框使用 */
    @Transactional(readOnly = true)
    public List<TeacherVO> listAll() {
        return teacherRepo.findAll(Sort.by("id")).stream().map(TeacherVO::of).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public TeacherVO create(TeacherDTO dto) {
        if (teacherRepo.existsByTeacherNo(dto.getTeacherNo().trim())) {
            throw new BusinessException(ErrorCode.DUPLICATE_KEY, "工号已存在：" + dto.getTeacherNo());
        }
        String username = StringUtils.hasText(dto.getUsername())
                ? dto.getUsername().trim() : dto.getTeacherNo().trim();
        if (userRepo.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.DUPLICATE_KEY, "登录账号已存在：" + username);
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(
                StringUtils.hasText(dto.getPassword()) ? dto.getPassword() : DEFAULT_PASSWORD));
        user.setRole("TEACHER");
        user.setRealName(dto.getName());
        user.setStatus(1);
        userRepo.save(user);

        Teacher teacher = new Teacher();
        teacher.setUserId(user.getId());
        teacher.setTeacherNo(dto.getTeacherNo().trim());
        teacher.setName(dto.getName().trim());
        teacher.setTitle(dto.getTitle());
        teacher.setDept(dto.getDept().trim());
        teacherRepo.save(teacher);

        return TeacherVO.of(teacher);
    }

    @Transactional(rollbackFor = Exception.class)
    public TeacherVO update(Long id, TeacherDTO dto) {
        Teacher teacher = teacherRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "教师不存在"));

        if (!teacher.getTeacherNo().equals(dto.getTeacherNo().trim())
                && teacherRepo.existsByTeacherNo(dto.getTeacherNo().trim())) {
            throw new BusinessException(ErrorCode.DUPLICATE_KEY, "工号已存在：" + dto.getTeacherNo());
        }

        teacher.setTeacherNo(dto.getTeacherNo().trim());
        teacher.setName(dto.getName().trim());
        teacher.setTitle(dto.getTitle());
        teacher.setDept(dto.getDept().trim());
        teacherRepo.save(teacher);

        userRepo.findById(teacher.getUserId()).ifPresent(user -> {
            user.setRealName(teacher.getName());
            userRepo.save(user);
        });

        return TeacherVO.of(teacher);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Teacher teacher = teacherRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "教师不存在"));

        long courseCount = courseRepo.countByTeacherId(id);
        if (courseCount > 0) {
            throw new BusinessException(ErrorCode.COURSE_DELETE_FORBIDDEN,
                    "该教师还有 " + courseCount + " 门课程，请先转移或删除课程");
        }

        teacherRepo.delete(teacher);
        userRepo.deleteById(teacher.getUserId());
    }
}
