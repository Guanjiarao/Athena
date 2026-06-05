import type { UserRole } from '@/contexts/AuthContext'

/** 临时：接口未返回角色时，该手机号视为管理员 */
export const ADMIN_PHONE = '12345678912'

export function resolveRoleFromPhone(phone: string): UserRole {
  const digits = phone.replace(/\D/g, '')
  return digits === ADMIN_PHONE ? 'admin' : 'kol'
}
