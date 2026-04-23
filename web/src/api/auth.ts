export interface AuthStatus {
  hasPasswordLogin: boolean
  username: string | null
}

export async function fetchAuthStatus(): Promise<AuthStatus> {
  return {
    hasPasswordLogin: false,
    username: null,
  }
}

function unsupported(): never {
  throw new Error('当前后端未启用密码登录')
}

export async function loginWithPassword(_username: string, _password: string): Promise<string> {
  unsupported()
}

export async function setupPassword(_username: string, _password: string): Promise<void> {
  unsupported()
}

export async function changePassword(_currentPassword: string, _newPassword: string): Promise<void> {
  unsupported()
}

export async function changeUsername(_currentPassword: string, _newUsername: string): Promise<void> {
  unsupported()
}

export async function removePassword(): Promise<void> {
  unsupported()
}
