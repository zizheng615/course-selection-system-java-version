package com.example.cs.repository;

import com.example.cs.entity.SysUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    Optional<SysUser> findByUsername(String username);

    boolean existsByUsername(String username);

    /**
     * 账号列表：左连接学生表与教师表，把"这个账号对应哪条业务数据"一并查出来。
     *
     * <p>用 LEFT JOIN 而非 INNER JOIN：管理员账号在两张业务表里都没有记录，
     * 用 INNER JOIN 会把管理员从列表里漏掉。
     *
     * <p>排序用 FIELD() 指定角色优先级（管理员 → 教师 → 学生），
     * 比按字母序更符合使用习惯。
     */
    @Query(value = """
            SELECT u.id, u.username, u.role, u.real_name, u.status, u.created_at,
                   s.student_no, s.major, t.teacher_no, t.dept
              FROM sys_user u
              LEFT JOIN student s ON s.user_id = u.id
              LEFT JOIN teacher t ON t.user_id = u.id
             WHERE (:keyword = '' OR u.username  LIKE CONCAT('%', :keyword, '%')
                                 OR u.real_name LIKE CONCAT('%', :keyword, '%'))
               AND (:role = '' OR u.role = :role)
             ORDER BY FIELD(u.role, 'ADMIN', 'TEACHER', 'STUDENT'), u.id
            """,
            countQuery = """
            SELECT COUNT(*)
              FROM sys_user u
             WHERE (:keyword = '' OR u.username  LIKE CONCAT('%', :keyword, '%')
                                 OR u.real_name LIKE CONCAT('%', :keyword, '%'))
               AND (:role = '' OR u.role = :role)
            """,
            nativeQuery = true)
    Page<Object[]> searchUsers(@Param("keyword") String keyword,
                               @Param("role") String role,
                               Pageable pageable);
}
