import request from './request'

export const pageStudents = (params) => request.get('/students', { params })

export const getMajors = () => request.get('/students/majors')

export const createStudent = (data) => request.post('/students', data)

export const updateStudent = (id, data) => request.put(`/students/${id}`, data)

export const deleteStudent = (id) => request.delete(`/students/${id}`)
