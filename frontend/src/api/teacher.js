import request from './request'

export const pageTeachers = (params) => request.get('/teachers', { params })

export const getAllTeachers = () => request.get('/teachers/all')

export const createTeacher = (data) => request.post('/teachers', data)

export const updateTeacher = (id, data) => request.put(`/teachers/${id}`, data)

export const deleteTeacher = (id) => request.delete(`/teachers/${id}`)
