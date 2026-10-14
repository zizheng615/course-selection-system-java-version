import request from './request'

export const listSchedules = (courseId) =>
  request.get('/schedules', { params: { courseId } })

export const createSchedule = (data) => request.post('/schedules', data)

export const updateSchedule = (id, data) => request.put(`/schedules/${id}`, data)

export const deleteSchedule = (id) => request.delete(`/schedules/${id}`)
