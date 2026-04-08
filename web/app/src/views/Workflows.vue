<template>
  <div class="workflows">
    <el-card>
      <template #header>
        <span>工作流记录</span>
      </template>

      <el-form :inline="true" :model="queryForm">
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="全部" clearable>
            <el-option label="待处理" value="pending" />
            <el-option label="运行中" value="running" />
            <el-option label="已完成" value="completed" />
            <el-option label="失败" value="failed" />
            <el-option label="已取消" value="cancelled" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadWorkflows">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="workflows" style="width: 100%" v-loading="loading">
        <el-table-column prop="workflowName" label="工作流名称" />
        <el-table-column prop="currentStep" label="当前步骤" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="180" />
        <el-table-column prop="endTime" label="结束时间" width="180" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button text type="primary" @click="viewDetail(row.id)">查看详情</el-button>
            <el-button
              v-if="row.status === 'running'"
              text
              type="danger"
              @click="cancelWorkflow(row.id)"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryForm.pageNum"
        v-model:page-size="queryForm.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadWorkflows"
        @current-change="loadWorkflows"
        style="margin-top: 20px; justify-content: center"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { workflowApi } from '../api'

const router = useRouter()
const loading = ref(false)
const workflows = ref([])
const total = ref(0)

const queryForm = ref({
  pageNum: 1,
  pageSize: 10,
  status: ''
})

const loadWorkflows = async () => {
  loading.value = true
  try {
    const res = await workflowApi.getPage(queryForm.value)
    workflows.value = res.data.records
    total.value = res.data.total
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

const viewDetail = (id) => {
  router.push(`/workflow/${id}`)
}

const cancelWorkflow = async (id) => {
  try {
    await ElMessageBox.confirm('确定要取消该工作流吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await workflowApi.cancel(id)
    ElMessage.success('取消成功')
    loadWorkflows()
  } catch (error) {
    if (error !== 'cancel') {
      console.error(error)
    }
  }
}

const getStatusType = (status) => {
  const map = {
    'pending': 'info',
    'running': 'warning',
    'completed': 'success',
    'failed': 'danger',
    'cancelled': 'info'
  }
  return map[status] || 'info'
}

const getStatusText = (status) => {
  const map = {
    'pending': '待处理',
    'running': '运行中',
    'completed': '已完成',
    'failed': '失败',
    'cancelled': '已取消'
  }
  return map[status] || status
}

onMounted(() => {
  loadWorkflows()
})
</script>

<style scoped>
.workflows {
  max-width: 1400px;
  margin: 0 auto;
}
</style>
