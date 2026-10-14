package com.example.cs;

import com.example.cs.common.BusinessException;
import com.example.cs.entity.Course;
import com.example.cs.repository.CourseRepository;
import com.example.cs.repository.CourseScheduleRepository;
import com.example.cs.repository.EnrollmentRepository;
import com.example.cs.service.EnrollmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 并发选课测试 —— 本项目最有说服力的一组验证。
 *
 * <p><b>验证的是「超卖」问题。</b>把一门课的容量设为很小的值，
 * 然后让远多于容量的线程同时抢。如果扣减逻辑写成「先 SELECT 查余量、再 UPDATE」，
 * 就会有多个事务读到同一个余量值并同时通过校验，最终选课人数超过容量：
 *
 * <pre>
 *   事务 A: SELECT enrolled_count → 9   (capacity = 10，判断"还有名额")
 *   事务 B: SELECT enrolled_count → 9   (同样判断"还有名额")
 *   事务 A: UPDATE enrolled_count = 10
 *   事务 B: UPDATE enrolled_count = 11  ← 超卖
 * </pre>
 *
 * <p>本实现把「判断 + 扣减」压进一条原子 UPDATE，靠 InnoDB 行锁保证原子性，
 * 因此断言应当是：成功次数恰好等于容量，且计数不超容量、与真实选课记录数一致。
 *
 * <p><b>自清理：</b>测试使用自己临时创建的一门课程，结束后连同选课记录一并删除，
 * 不会污染 02_seed_data.sql 灌入的演示数据。
 *
 * <p>运行前提：需要可连接的 MySQL（见 application.yml）。
 * 若只想跑不依赖数据库的测试：{@code mvn test -Dtest='!EnrollmentConcurrencyTest'}
 */
@SpringBootTest
class EnrollmentConcurrencyTest {

    /** 临时课程代码，测试结束后会被删除 */
    private static final String TEST_CODE = "TEST-CONCURRENT";
    private static final String SEMESTER = "2026-2027-1";
    private static final int THREADS = 100;
    private static final int CAPACITY = 10;
    private static final int STUDENT_POOL = 50;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private CourseScheduleRepository scheduleRepo;

    @Autowired
    private EnrollmentRepository enrollRepo;

    @Test
    @DisplayName("100 个线程抢 10 个名额：成功次数恰好为 10，不超卖")
    void shouldNotOversellUnderConcurrency() throws InterruptedException {
        Long courseId = prepareCourse();

        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        // CountDownLatch 制造「同时起跑」的效果。
        // 若没有这道闸门，线程会近似串行执行，根本测不出竞态。
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        try {
            for (int i = 0; i < THREADS; i++) {
                final long studentId = 1 + (i % STUDENT_POOL);
                pool.submit(() -> {
                    try {
                        startGate.await();
                        enrollmentService.selectCourse(studentId, courseId);
                        success.incrementAndGet();
                    } catch (BusinessException e) {
                        // 容量已满 / 重复选课 —— 都是预期内的失败
                        rejected.incrementAndGet();
                    } catch (Exception e) {
                        rejected.incrementAndGet();
                    } finally {
                        endGate.countDown();
                    }
                });
            }

            startGate.countDown();   // 发令枪响，100 个线程同时冲
            assertTrue(endGate.await(120, TimeUnit.SECONDS), "并发选课超时未完成");
        } finally {
            pool.shutdownNow();
        }

        Course after = courseRepo.findById(courseId).orElseThrow();
        long realCount = enrollRepo.countByCourseIdAndStatus(courseId, "SELECTED");

        System.out.printf("%n================ 并发选课结果 ================%n");
        System.out.printf("  并发线程数      : %d%n", THREADS);
        System.out.printf("  课程容量        : %d%n", CAPACITY);
        System.out.printf("  选课成功        : %d%n", success.get());
        System.out.printf("  被拒绝          : %d%n", rejected.get());
        System.out.printf("  enrolled_count  : %d%n", after.getEnrolledCount());
        System.out.printf("  实际选课记录数  : %d%n", realCount);
        System.out.printf("=============================================%n%n");

        // 核心断言 1：成功次数恰好等于容量 —— 多于说明超卖，少于说明名额被浪费
        assertEquals(CAPACITY, success.get(),
                "成功选课次数应恰好等于容量 " + CAPACITY + "，实际=" + success.get());

        // 核心断言 2：冗余计数字段没有突破容量上限
        assertTrue(after.getEnrolledCount() <= CAPACITY,
                "enrolled_count 超过容量上限，出现超卖！实际=" + after.getEnrolledCount());

        // 核心断言 3：反范式字段与真实选课记录数一致，未发生失真
        assertEquals(after.getEnrolledCount().longValue(), realCount,
                "enrolled_count 与真实选课记录数不一致，扣减与写入出现偏差");

        cleanUp(courseId);
    }

    /** 临时建一门容量为 CAPACITY 的课程，避免改动演示数据 */
    private Long prepareCourse() {
        removeLeftoverTestCourse();

        Course course = new Course();
        course.setCourseCode(TEST_CODE);
        course.setName("并发测试专用课程");
        course.setCredit(new BigDecimal("1.0"));
        course.setHours(16);
        course.setCourseType("选修");
        course.setDept("测试学院");
        course.setSemester(SEMESTER);
        course.setTeacherId(1L);
        course.setCapacity(CAPACITY);
        course.setEnrolledCount(0);
        course.setStatus("OPEN");
        course.setIntro("由 EnrollmentConcurrencyTest 自动创建，测试结束后删除");
        return courseRepo.save(course).getId();
    }

    /** 删除临时课程及其选课记录，恢复数据库原状 */
    private void cleanUp(Long courseId) {
        enrollRepo.deleteAll(enrollRepo.findByCourseIdAndStatus(courseId, "SELECTED"));
        scheduleRepo.deleteByCourseId(courseId);
        courseRepo.deleteById(courseId);
    }

    /** 清理上一次运行可能残留的测试课程（例如中途断言失败） */
    private void removeLeftoverTestCourse() {
        courseRepo.findByCourseCodeAndSemester(TEST_CODE, SEMESTER).ifPresent(c -> {
            enrollRepo.deleteAll(enrollRepo.findByCourseIdAndStatus(c.getId(), "SELECTED"));
            scheduleRepo.deleteByCourseId(c.getId());
            courseRepo.deleteById(c.getId());
        });
    }
}
