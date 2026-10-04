export interface UserInfo {
  id: number
  phone: string
}

export interface LoginResult {
  accessToken: string
  refreshToken: string
  userInfo: UserInfo
}
