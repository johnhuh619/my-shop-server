export const formatDateTime = (iso: string | null | undefined) => {
  if (!iso) {
    return '-'
  }

  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return iso
  }

  return date.toLocaleString('ko-KR')
}

