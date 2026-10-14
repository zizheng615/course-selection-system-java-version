import request from './request'

export const getPopularCourses = (params) => request.get('/stats/popular-courses', { params })

export const getMajorDistribution = () => request.get('/stats/major-distribution')

export const getTeacherWorkload = (params) => request.get('/stats/teacher-workload', { params })
