const dateTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
})

export function formatDateTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : dateTimeFormatter.format(date)
}

export function formatScore(value) {
  const score = Number(value || 0)
  return score > 0 ? score.toFixed(1) : '暂无'
}

export function statusLabel(status) {
  return ({
    PENDING: '待审核',
    APPROVED: '已通过',
    REJECTED: '已拒绝',
    DISABLED: '已停用',
    DRAFT: '草稿',
    PUBLISHED: '已上架',
    DELISTED: '已下架',
    PENDING_REVIEW: '版本审核中',
    VISIBLE: '公开可见',
  })[status] || status || '未知'
}

export function statusTone(status) {
  if (['APPROVED', 'PUBLISHED', 'VISIBLE'].includes(status)) return 'positive'
  if (['REJECTED', 'DISABLED', 'DELISTED'].includes(status)) return 'negative'
  if (['PENDING', 'PENDING_REVIEW'].includes(status)) return 'warning'
  return 'neutral'
}

export function asList(value) {
  if (Array.isArray(value)) return value
  if (Array.isArray(value?.items)) return value.items
  if (Array.isArray(value?.content)) return value.content
  return []
}
