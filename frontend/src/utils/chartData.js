function mergeExtraJson(existing, incoming) {
  if (!incoming) return existing
  if (!existing) return incoming
  try {
    const a = JSON.parse(existing)
    const b = JSON.parse(incoming)
    return JSON.stringify({ ...a, ...b })
  } catch {
    return incoming
  }
}

export function aggregateByDate(records) {
  const map = new Map()
  for (const r of records) {
    const cur = map.get(r.dateStr) || {
      dateStr: r.dateStr,
      tokensIn: 0,
      tokensOut: 0,
      requests: 0,
      costUsd: 0,
      extraJson: ''
    }
    cur.tokensIn += r.tokensIn || 0
    cur.tokensOut += r.tokensOut || 0
    cur.requests += r.requests || 0
    cur.costUsd += r.costUsd || 0
    cur.extraJson = mergeExtraJson(cur.extraJson, r.extraJson)
    map.set(r.dateStr, cur)
  }
  return [...map.values()].sort((a, b) => a.dateStr.localeCompare(b.dateStr))
}

export function getBalanceHistory(records) {
  return records
    .filter(r => {
      try {
        const extra = JSON.parse(r.extraJson || '{}')
        return extra.total_balance !== undefined
      } catch {
        return false
      }
    })
    .sort((a, b) => a.dateStr.localeCompare(b.dateStr))
}

export function formatChartDate(dateStr) {
  const parts = dateStr.split('-')
  return parts.length >= 3 ? `${parts[1]}-${parts[2]}` : dateStr
}

export function hasDeepSeekCsvData(records) {
  return records.some(r => r.model === 'csv-import' || (r.tokensIn || 0) + (r.tokensOut || 0) + (r.requests || 0) > 0)
}