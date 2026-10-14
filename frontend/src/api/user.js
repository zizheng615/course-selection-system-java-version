import request from './request'

export const pageUsers = (params) => request.get('/users', { params })

/** 修改登录账号 / 重置密码；password 传 null 表示只改账号 */
export const updateUserCredentials = (id, data) =>
  request.put(`/users/${id}/credentials`, data)

export const updateUserStatus = (id, status) =>
  request.patch(`/users/${id}/status`, null, { params: { status } })

export const deleteUser = (id) => request.delete(`/users/${id}`)
