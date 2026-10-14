import request from './request'

/** 提问。角色由后端从 JWT 解析，前端无需传递 */
export const askAi = (question) => request.post('/ai/chat', { question })

/** 当前角色的推荐问题 */
export const getAiSuggestions = () => request.get('/ai/suggestions')
