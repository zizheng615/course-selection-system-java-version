import request from './request'

export const selectCourse = (courseId) => request.post('/enrollments', { courseId })

export const withdrawCourse = (courseId) => request.delete(`/enrollments/${courseId}`)

export const getMyCourses = (params) => request.get('/enrollments/my', { params })

export const getMyTimetable = (params) => request.get('/enrollments/my/timetable', { params })

export const getAvailableCourses = (params) => request.get('/enrollments/available', { params })

export const getConflictingCourses = (params) => request.get('/enrollments/conflicts', { params })

export const getCourseStudents = (courseId) => request.get(`/courses/${courseId}/students`)

export const recordScore = (data) => request.patch('/enrollments/score', data)

// ---------------- 管理员：选课记录管理 ----------------

export const pageEnrollments = (params) => request.get('/enrollments', { params })

/** 代学生加选课，与学生端走相同的容量/冲突/重复校验 */
export const adminEnroll = (data) => request.post('/enrollments/admin', data)

/** 软退课：置为 WITHDRAWN 并归还容量，保留修读历史 */
export const adminWithdraw = (id) => request.patch(`/enrollments/record/${id}/withdraw`)

/** 硬删记录：整行删除，用于清理误录数据 */
export const adminDeleteEnrollment = (id) => request.delete(`/enrollments/record/${id}`)
