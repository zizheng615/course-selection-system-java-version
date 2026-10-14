package com.example.cs.service;

import com.example.cs.common.BusinessException;
import com.example.cs.common.ErrorCode;
import com.example.cs.dto.LoginDTO;
import com.example.cs.entity.Student;
import com.example.cs.entity.SysUser;
import com.example.cs.entity.Teacher;
import com.example.cs.repository.StudentRepository;
import com.example.cs.repository.SysUserRepository;
import com.example.cs.repository.TeacherRepository;
import com.example.cs.security.JwtUtil;
import com.example.cs.security.LoginUser;
import com.example.cs.security.UserContext;
import com.example.cs.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserRepository userRepo;
    private final StudentRepository studentRepo;
    private final TeacherRepository teacherRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional(readOnly = true)
    public LoginVO login(LoginDTO dto) {
        // 账号不存在与密码错误返回同一提示，避免账号枚举
        SysUser user = userRepo.findByUsername(dto.getUsername().trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED, "账号或密码错误"));

        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED, "该账号已被禁用，请联系管理员");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED, "账号或密码错误");
        }

        // 把业务主键写进 Token：后续接口直接用 refId 定位学生/教师，无需再查一次库
        Long refId = switch (user.getRole()) {
            case "STUDENT" -> studentRepo.findByUserId(user.getId())
                    .map(Student::getId).orElse(null);
            case "TEACHER" -> teacherRepo.findByUserId(user.getId())
                    .map(Teacher::getId).orElse(null);
            default -> null;
        };

        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(),
                user.getRole(), user.getRealName(), refId);
        String token = jwtUtil.generate(loginUser);
        return LoginVO.of(token, loginUser, jwtUtil.getExpireHours());
    }

    /** 当前登录用户信息（用于前端刷新页面后恢复状态）。 */
    public LoginVO currentUser() {
        LoginUser u = UserContext.require();
        return LoginVO.of(null, u, null);
    }
}
