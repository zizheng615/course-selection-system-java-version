<template>
  <!-- ============ 悬浮入口按钮 ============ -->
  <button
    v-show="!open"
    class="fab"
    type="button"
    title="AI 助手 · 解答系统使用问题"
    @click="openPanel"
  >
    <span class="fab__halo"></span>
    <svg class="fab__icon" viewBox="0 0 24 24" aria-hidden="true">
      <!-- 四角星芒：AI 助手的通用视觉符号 -->
      <path
        d="M12 2.2c.8 5.2 3.6 8 8.8 8.8-5.2.8-8 3.6-8.8 8.8-.8-5.2-3.6-8-8.8-8.8 5.2-.8 8-3.6 8.8-8.8Z"
        fill="currentColor"
      />
      <path
        d="M18.6 2.4c.3 1.9 1.3 2.9 3.2 3.2-1.9.3-2.9 1.3-3.2 3.2-.3-1.9-1.3-2.9-3.2-3.2 1.9-.3 2.9-1.3 3.2-3.2Z"
        fill="currentColor"
        opacity=".75"
      />
    </svg>
  </button>

  <!-- ============ 右侧对话抽屉 ============ -->
  <Teleport to="body">
    <Transition name="drawer">
      <aside v-if="open" class="drawer" :style="{ width: DRAWER_WIDTH + 'px' }">
        <header class="drawer__head">
          <div class="drawer__title">
            <span class="drawer__mark">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path
                  d="M12 2.2c.8 5.2 3.6 8 8.8 8.8-5.2.8-8 3.6-8.8 8.8-.8-5.2-3.6-8-8.8-8.8 5.2-.8 8-3.6 8.8-8.8Z"
                  fill="currentColor"
                />
              </svg>
            </span>
            <div>
              <div class="drawer__name">AI 助手</div>
              <div class="drawer__desc">解答本系统的使用方法</div>
            </div>
          </div>
          <div class="drawer__tools">
            <el-tooltip content="清空对话" placement="bottom">
              <el-button link :icon="Delete" @click="clearChat" />
            </el-tooltip>
            <el-tooltip content="关闭" placement="bottom">
              <el-button link :icon="Close" @click="closePanel" />
            </el-tooltip>
          </div>
        </header>

        <div ref="bodyRef" class="drawer__body">
          <div
            v-for="(msg, i) in messages"
            :key="i"
            class="msg"
            :class="msg.role === 'user' ? 'msg--user' : 'msg--ai'"
          >
            <div class="msg__bubble">
              <div v-if="msg.role === 'ai'" class="msg__source">
                <span class="msg__dot"></span>{{ msg.source }}
              </div>
              <div class="msg__text" v-html="render(msg.text)"></div>
            </div>

            <!-- 追问建议 -->
            <div v-if="msg.suggestions?.length" class="chips">
              <button
                v-for="s in msg.suggestions"
                :key="s"
                class="chip"
                type="button"
                @click="send(s)"
              >
                {{ s }}
              </button>
            </div>
          </div>

          <div v-if="loading" class="msg msg--ai">
            <div class="msg__bubble msg__bubble--typing">
              <span></span><span></span><span></span>
            </div>
          </div>
        </div>

        <footer class="drawer__foot">
          <el-input
            v-model="draft"
            type="textarea"
            :rows="2"
            resize="none"
            maxlength="500"
            placeholder="问点什么…（Enter 发送，Shift+Enter 换行）"
            @keydown.enter.exact.prevent="send()"
          />
          <div class="drawer__send">
            <span class="drawer__hint">{{ draft.length }}/500</span>
            <el-button
              type="primary"
              :icon="Promotion"
              :disabled="!draft.trim()"
              :loading="loading"
              @click="send()"
            >
              发送
            </el-button>
          </div>
        </footer>
      </aside>
    </Transition>
  </Teleport>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { Close, Delete, Promotion } from '@element-plus/icons-vue'
import { askAi, getAiSuggestions } from '@/api/ai'
import { useUserStore } from '@/stores/user'

/** 抽屉宽度，与 MainLayout 的布局挤压宽度必须一致 */
const DRAWER_WIDTH = 400

const props = defineProps({
  open: { type: Boolean, default: false }
})
const emit = defineEmits(['update:open'])

const userStore = useUserStore()
const messages = ref([])
const draft = ref('')
const loading = ref(false)
const bodyRef = ref()
const greeted = ref(false)

function openPanel() {
  emit('update:open', true)
  if (!greeted.value) {
    greeted.value = true
    greet()
  }
  scrollToBottom()
}

function closePanel() {
  emit('update:open', false)
}

async function greet() {
  let suggestions = []
  try {
    suggestions = await getAiSuggestions()
  } catch {
    // 拉不到推荐问题不影响对话，退化成一条纯欢迎语
  }
  messages.value.push({
    role: 'ai',
    source: '本地知识库',
    text: `你好，${userStore.realName || ''} 👋\n\n我是本选课系统的使用助手，可以帮你解答操作上的问题。`,
    suggestions
  })
  scrollToBottom()
}

async function send(preset) {
  const question = (preset ?? draft.value).trim()
  if (!question || loading.value) return

  messages.value.push({ role: 'user', text: question })
  draft.value = ''
  loading.value = true
  scrollToBottom()

  try {
    const res = await askAi(question)
    messages.value.push({
      role: 'ai',
      text: res.answer,
      source: res.source,
      suggestions: res.suggestions
    })
  } catch {
    // 网络/服务异常时给一条本地兜底，避免对话直接断掉
    messages.value.push({
      role: 'ai',
      source: '本地知识库',
      text: '抱歉，我这边出了点问题，暂时回答不了。可以稍后再试，或联系管理员。'
    })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

function clearChat() {
  messages.value = []
  greeted.value = false
  greet()
}

async function scrollToBottom() {
  await nextTick()
  const el = bodyRef.value
  if (el) el.scrollTop = el.scrollHeight
}

/**
 * 极简富文本渲染：只支持回答里实际会用到的 **加粗** 与换行。
 * 先转义 HTML 再替换，避免把内容当成 HTML 注入。
 */
function render(text) {
  if (!text) return ''
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\n/g, '<br>')
}

onMounted(() => {
  if (props.open) openPanel()
})
</script>

<style scoped>
/* ================= 悬浮按钮 ================= */

.fab {
  position: fixed;
  right: 28px;
  bottom: 28px;
  z-index: 2000;
  width: 54px;
  height: 54px;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  color: #fff;
  background: linear-gradient(140deg, var(--brand-500) 0%, #5b9df9 100%);
  box-shadow:
    0 6px 18px rgba(59, 111, 224, 0.42),
    0 2px 6px rgba(59, 111, 224, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.22s ease, box-shadow 0.22s ease;
}

.fab:hover {
  transform: translateY(-2px) scale(1.04);
  box-shadow:
    0 10px 26px rgba(59, 111, 224, 0.5),
    0 3px 8px rgba(59, 111, 224, 0.34);
}

.fab:active {
  transform: scale(0.98);
}

.fab__icon {
  width: 26px;
  height: 26px;
  position: relative;
  z-index: 1;
}

/* 呼吸光晕：给按钮一点「在线/智能」的科技感 */
.fab__halo {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: rgba(59, 111, 224, 0.45);
  animation: fab-pulse 2.4s ease-out infinite;
}

@keyframes fab-pulse {
  0% {
    transform: scale(1);
    opacity: 0.55;
  }
  70% {
    transform: scale(1.7);
    opacity: 0;
  }
  100% {
    transform: scale(1.7);
    opacity: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .fab__halo {
    animation: none;
  }
}

/* ================= 抽屉 ================= */

.drawer {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 2100;
  display: flex;
  flex-direction: column;
  background-color: var(--c-surface);
  border-left: 1px solid var(--c-border);
  box-shadow: -6px 0 24px rgba(16, 24, 40, 0.08);
}

.drawer__head {
  flex-shrink: 0;
  height: 64px;
  padding: 0 14px 0 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--c-border-light);
}

.drawer__title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.drawer__mark {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: linear-gradient(140deg, var(--brand-500) 0%, #5b9df9 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.drawer__mark svg {
  width: 17px;
  height: 17px;
}

.drawer__name {
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text);
  line-height: 1.3;
}

.drawer__desc {
  font-size: 11.5px;
  color: var(--c-text-3);
  line-height: 1.3;
}

.drawer__tools {
  display: flex;
  gap: 2px;
}

.drawer__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px;
  background-color: var(--c-page-bg);
}

/* ================= 消息 ================= */

.msg {
  display: flex;
  flex-direction: column;
  margin-bottom: 14px;
}

.msg--user {
  align-items: flex-end;
}

.msg--ai {
  align-items: flex-start;
}

.msg__bubble {
  max-width: 88%;
  padding: 10px 13px;
  border-radius: 12px;
  font-size: 13.5px;
  line-height: 1.65;
  word-break: break-word;
}

.msg--user .msg__bubble {
  background: linear-gradient(140deg, var(--brand-500) 0%, #5b9df9 100%);
  color: #fff;
  border-bottom-right-radius: 3px;
}

.msg--ai .msg__bubble {
  background-color: var(--c-surface);
  color: var(--c-text);
  border: 1px solid var(--c-border-light);
  border-bottom-left-radius: 3px;
}

.msg__source {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-bottom: 6px;
  font-size: 11px;
  color: var(--c-text-3);
}

.msg__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background-color: var(--c-success);
}

.msg__text :deep(strong) {
  color: var(--brand-600);
  font-weight: 600;
}

.msg--user .msg__text :deep(strong) {
  color: #fff;
}

/* 输入指示器 */
.msg__bubble--typing {
  display: flex;
  gap: 4px;
  padding: 14px 14px;
}

.msg__bubble--typing span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: var(--c-text-3);
  animation: typing 1.2s infinite ease-in-out;
}

.msg__bubble--typing span:nth-child(2) {
  animation-delay: 0.16s;
}

.msg__bubble--typing span:nth-child(3) {
  animation-delay: 0.32s;
}

@keyframes typing {
  0%,
  60%,
  100% {
    opacity: 0.3;
    transform: translateY(0);
  }
  30% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

/* 追问建议 */
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
  max-width: 94%;
}

.chip {
  padding: 5px 11px;
  border-radius: 14px;
  border: 1px solid var(--el-color-primary-light-7);
  background-color: var(--el-color-primary-light-9);
  color: var(--brand-600);
  font-size: 12.5px;
  cursor: pointer;
  transition: all 0.18s;
  font-family: inherit;
}

.chip:hover {
  background-color: var(--el-color-primary-light-8);
  border-color: var(--el-color-primary-light-5);
}

/* ================= 输入区 ================= */

.drawer__foot {
  flex-shrink: 0;
  padding: 12px 14px 14px;
  border-top: 1px solid var(--c-border-light);
  background-color: var(--c-surface);
}

.drawer__send {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.drawer__hint {
  font-size: 11.5px;
  color: var(--c-text-3);
}

/* ================= 抽屉动画 ================= */

.drawer-enter-active,
.drawer-leave-active {
  transition: transform 0.26s cubic-bezier(0.4, 0, 0.2, 1);
}

.drawer-enter-from,
.drawer-leave-to {
  transform: translateX(100%);
}
</style>
