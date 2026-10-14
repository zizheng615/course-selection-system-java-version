package com.example.cs.service;

import com.example.cs.common.BusinessException;
import com.example.cs.common.ErrorCode;
import com.example.cs.common.PageResult;
import com.example.cs.dto.StudentDTO;
import com.example.cs.entity.Student;
import com.example.cs.entity.SysUser;
import com.example.cs.repository.EnrollmentRepository;
import com.example.cs.repository.StudentRepository;
import com.example.cs.repository.SysUserRepository;
import com.example.cs.vo.StudentVO;
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
public class StudentService {

    private static final String DEFAULT_PASSWORD = "123456";

    private final StudentRepository studentRepo;
    private final SysUserRepository userRepo;
    private final EnrollmentRepository enrollRepo;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public PageResult<StudentVO> page(String keyword, String major, int page, int size) {
        Specification<Student> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("name"), like),
                        cb.like(root.get("studentNo"), like)));
            }
            if (StringUtils.hasText(major)) {
                predicates.add(cb.equal(root.get("major"), major.trim()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Student> result = studentRepo.findAll(spec,
                PageRequest.of(Math.max(page - 1, 0), size, Sort.by("id")));
        return PageResult.of(result, StudentVO::of);
    }

    @Transactional(readOnly = true)
    public List<String> majors() {
        return studentRepo.findDistinctMajors();
    }

    @Transactional(rollbackFor = Exception.class)
    public StudentVO create(StudentDTO dto) {
        if (studentRepo.existsByStudentNo(dto.getStudentNo().trim())) {
            throw new BusinessException(ErrorCode.DUPLICATE_KEY, "学号已存在：" + dto.getStudentNo());
        }
        // 未单独指定登录账号时，默认用学号作为账号
        String username = StringUtils.hasText(dto.getUsername())
                ? dto.getUsername().trim() : dto.getStudentNo().trim();
        if (userRepo.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.DUPLICATE_KEY, "登录账号已存在：" + username);
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(
                StringUtils.hasText(dto.getPassword()) ? dto.getPassword() : DEFAULT_PASSWORD));
        user.setRole("STUDENT");
        user.setRealName(dto.getName());
        user.setStatus(1);
        userRepo.save(user);

        Student student = new Student();
        student.setUserId(user.getId());
        student.setStudentNo(dto.getStudentNo().trim());
        student.setName(dto.getName().trim());
        student.setGender(StringUtils.hasText(dto.getGender()) ? dto.getGender() : "M");
        student.setMajor(dto.getMajor().trim());
        student.setGrade(dto.getGrade().trim());
        student.setPhone(dto.getPhone());
        studentRepo.save(student);

        return StudentVO.of(student);
    }

    @Transactional(rollbackFor = Exception.class)
    public StudentVO update(Long id, StudentDTO dto) {
        Student student = studentRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "学生不存在"));

        if (!student.getStudentNo().equals(dto.getStudentNo().trim())
                && studentRepo.existsByStudentNo(dto.getStudentNo().trim())) {
            throw new BusinessException(ErrorCode.DUPLICATE_KEY, "学号已存在：" + dto.getStudentNo());
        }

        student.setStudentNo(dto.getStudentNo().trim());
        student.setName(dto.getName().trim());
        student.setGender(StringUtils.hasText(dto.getGender()) ? dto.getGender() : student.getGender());
        student.setMajor(dto.getMajor().trim());
        student.setGrade(dto.getGrade().trim());
        student.setPhone(dto.getPhone());
        studentRepo.save(student);

        // 同步更新账号表里的姓名，避免两处不一致
        userRepo.findById(student.getUserId()).ifPresent(user -> {
            user.setRealName(student.getName());
            userRepo.save(user);
        });

        return StudentVO.of(student);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Student student = studentRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "学生不存在"));

        // 外键为 RESTRICT，只要有选课记录（含已退课的历史记录）就无法删除。
        // 这是有意为之：选课记录是学生修读历史的凭据，不应随人员删除而丢失。
        long enrollCount = enrollRepo.countByStudentId(id);
        if (enrollCount > 0) {
            throw new BusinessException(ErrorCode.COURSE_DELETE_FORBIDDEN,
                    "该学生存在 " + enrollCount + " 条选课历史记录（已退课的记录同样计入），"
                            + "为保留修读记录不允许删除");
        }

        studentRepo.delete(student);
        userRepo.deleteById(student.getUserId());
    }
}
