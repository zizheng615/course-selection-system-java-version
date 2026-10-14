package com.example.cs.service;

import com.example.cs.common.BusinessException;
import com.example.cs.common.ErrorCode;
import com.example.cs.common.PageResult;
import com.example.cs.dto.UserCredentialDTO;
import com.example.cs.entity.SysUser;
import com.example.cs.repository.StudentRepository;
import com.example.cs.repository.SysUserRepository;
import com.example.cs.repository.TeacherRepository;
import com.example.cs.security.UserContext;
import com.example.cs.support.SqlUtils;
import com.example.cs.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * 账号管理（仅管理员）。
 *
 * <p>与 StudentService / TeacherService 的分工：后两者管的是**业务数据**
 * （学号、专业、职称等），本类管的是**登录凭据**（账号、密码、启用状态）。
 * 两边的删除都要经过引用检查，避免留下悬空的账号或业务记录。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserRepository userRepo;
    private final StudentRepository studentRepo;
    private final TeacherRepository teacherRepo;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public PageResult<UserVO> page(String keyword, String role, int page, int size) {
        Page<Object[]> result = userRepo.searchUsers(
                SqlUtils.blankIfNull(keyword),
                SqlUtils.blankIfNull(role),
                PageRequest.of(Math.max(page - 1, 0), size));
        return PageResult.of(result, UserVO::ofRow);
    }

    /**
     * 修改登录账号，并按需重置密码。
     *
     * <p>password 留空表示只改账号、不动密码 —— 这是刻意的：
     * 管理员想给某个人换个登录名时，不应该被迫知道或重设他的密码。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateCredentials(Long id, UserCredentialDTO dto) {
        SysUser user = userRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "账号不存在"));

        String newUsername = dto.getUsername().trim();
        if (!user.getUsername().equals(newUsername)) {
            userRepo.findByUsername(newUsername).ifPresent(other -> {
                if (!Objects.equals(other.getId(), id)) {
                    throw new BusinessException(ErrorCode.DUPLICATE_KEY,
                            "登录账号已被占用：" + newUsername);
                }
            });
            user.setUsername(newUsername);
        }

        if (StringUtils.hasText(dto.getPassword())) {
            user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        }

        userRepo.save(user);
    }

    /**
     * 启用 / 禁用账号。
     *
     * <p>禁止管理员禁用自己：这是最容易把自己锁在系统外的一种操作。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "状态只能为 1（启用）或 0（禁用）");
        }
        SysUser user = userRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "账号不存在"));

        if (Objects.equals(user.getId(), UserContext.require().userId()) && status == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不能禁用当前登录的账号");
        }

        user.setStatus(status);
        userRepo.save(user);
    }

    /**
     * 删除账号。
     *
     * <p>若账号已挂接学生或教师业务记录，必须先删业务记录（那个流程会连带删账号），
     * 否则会留下没有账号的业务数据，或撞上外键约束。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysUser user = userRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "账号不存在"));

        if (Objects.equals(user.getId(), UserContext.require().userId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不能删除当前登录的账号");
        }
        if (studentRepo.findByUserId(id).isPresent()) {
            throw new BusinessException(ErrorCode.COURSE_DELETE_FORBIDDEN,
                    "该账号已绑定学生信息，请在「学生管理」中删除对应学生");
        }
        if (teacherRepo.findByUserId(id).isPresent()) {
            throw new BusinessException(ErrorCode.COURSE_DELETE_FORBIDDEN,
                    "该账号已绑定教师信息，请在「教师管理」中删除对应教师");
        }

        userRepo.delete(user);
    }
}
