<template>
  <div class="logs">
    <el-card>
      <template #header>
        <span>系统日志</span>
      </template>

      <el-form :inline="true" :model="queryForm">
        <el-form-item label="用户ID">
          <el-input v-model="queryForm.userId" placeholder="请输入用户ID" clearable />
        </el-form-item>
        <el-form-item label="操作">
          <el-input v-model="queryForm.action" placeholder="请输入操作" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadLogs">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="logs" style="width: 100%" v-loading="loading">
        <el-table-column prop="userId" label="用户ID" width="200" />
        <el-table-column prop="action" label="操作" width="200" />
        <el-table-column prop="message" label="消息" />
        <el-table-column prop="createdTime" label="时间" width="180" />
      </el-table>

      <el-pagination
        v-model:current-page="queryForm.pageNum"
        v-model:page-size="queryForm.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadLogs"
        @current-change="loadLogs"
        style="margin-top: 20px; justify-content: center"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { logApi } from '../api'

const loading = ref(false)
const logs = ref([])
const total = ref(0)

const queryForm = ref({
  pageNum: 1,
  pageSize: 10,
  userId: '',
  action: ''
})

const loadLogs = async () => {
  loading.value = true
  try {
    const res = await logApi.getPage(queryForm.value)
    logs.value = res.data.records
    total.value = res.data.total
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadLogs()
})
</script>

<style scoped>
.logs {
  max-width: 1400px;
  margin: 0 auto;
}
</style>
