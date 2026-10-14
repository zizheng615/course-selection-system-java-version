import request from './request'

export const pageCourses = (params) => request.get('/courses', { params })

export const getCourse = (id) => request.get(`/courses/${id}`)

export const getSemesters = () => request.get('/courses/semesters')

export const getMyCourses = (params) => request.get('/courses/my', { params })

export const createCourse = (data) => request.post('/courses', data)

export const updateCourse = (id, data) => request.put(`/courses/${id}`, data)

export const updateCourseStatus = (id, status) =>
  request.patch(`/courses/${id}/status`, null, { params: { status } })

export const deleteCourse = (id) => request.delete(`/courses/${id}`)
