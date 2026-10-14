<template>
  <el-card>
    <el-form :inline="true" class="search-bar">
      <el-form-item label="关键词">
        <el-input
          v-model="query.keyword"
          placeholder="姓名 / 工号"
          clearable
          style="width: 200px"
          @keyup.enter="load(1)"
        />
      </el-form-item>
      <el-form-item label="院系">
        <el-input v-model="query.dept" placeholder="全部" clearable style="width: 180px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="load(1)">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
        <el-button type="success" @click="openDialog()">新增教师</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="teacherNo" label="工号" width="130" />
      <el-table-column prop="name" label="姓名" width="110" />
      <el-table-column prop="title" label="职称" width="120" />
      <el-table-column prop="dept" label="所属院系" min-width="160" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pagination"
      background
      layout="total, sizes, prev, pager, next"
      :total="total"
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      :page-sizes="[10, 20, 50]"
      @current-change="load()"
      @size-change="load(1)"
    />

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑教师' : '新增教师'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="工号" prop="teacherNo">
          <el-input v-model="form.teacherNo" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="职称">
          <el-select v-model="form.title" style="width: 100%">
            <el-option label="教授" value="教授" />
            <el-option label="副教授" value="副教授" />
            <el-option label="讲师" value="讲师" />
            <el-option label="助教" value="助教" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属院系" prop="dept">
          <el-input v-model="form.dept" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createTeacher,
  deleteTeacher,
  pageTeachers,
  updateTeacher
} from '@/api/teacher'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)

const query = reactive({ keyword: '', dept: '', page: 1, size: 10 })

const dialogVisible = ref(false)
const formRef = ref()
const emptyForm = () => ({ id: null, teacherNo: '', name: '', title: '讲师', dept: '' })
const form = reactive(emptyForm())

const rules = {
  teacherNo: [{ required: true, message: '请输入工号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  dept: [{ required: true, message: '请输入所属院系', trigger: 'blur' }]
}

async function load(page) {
  if (page) query.page = page
  loading.value = true
  try {
    const data = await pageTeachers(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.keyword = ''
  query.dept = ''
  load(1)
}

function openDialog(row) {
  Object.assign(form, row ? { ...row } : emptyForm())
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function handleSave() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.id) {
      await updateTeacher(form.id, form)
      ElMessage.success('修改成功')
    } else {
      await createTeacher(form)
      ElMessage.success('新增成功，初始密码 123456')
    }
    dialogVisible.value = false
    load()
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(
    `确定删除教师「${row.name}」吗？若名下有课程将被拒绝。`,
    '删除确认',
    { type: 'warning' }
  )
  await deleteTeacher(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(() => load())
</script>

<style scoped>
.search-bar {
  margin-bottom: 8px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
