// 认证工具类
const API_BASE = "http://123.57.19.160/api";
const COOKIE_NAME = "ltl_auth";

// 认证相关 API
export const authApi = {
  // 登录
  async login(playerName, password) {
    const response = await fetch(`${API_BASE}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ playerName, password })
    });

    const data = await response.json();
    if (data.code !== 200) {
      throw new Error(data.message || "登录失败");
    }
    return data.data;
  },

  // 登出
  async logout() {
    await fetch(`${API_BASE}/auth/logout`, {
      method: 'POST',
      credentials: 'include'
    });
  },

  // 修改密码
  async changePassword(currentPassword, newPassword, confirmPassword) {
    const response = await fetch(`${API_BASE}/user/change-password`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({
        currentPassword,
        newPassword,
        confirmPassword
      })
    });

    const data = await response.json();
    if (data.code !== 200) {
      throw new Error(data.message || "修改密码失败");
    }
    return data.data;
  },

  // 获取当前用户
  async getCurrentUser() {
    try {
      const response = await fetch(`${API_BASE}/auth/current`, {
        credentials: 'include'
      });

      const data = await response.json();
      if (data.code === 200) {
        return data.data;
      }
      return null;
    } catch (error) {
      console.error("获取用户信息失败:", error);
      return null;
    }
  },

  // 获取用户信息
  async getUserInfo() {
    try {
      const response = await fetch(`${API_BASE}/user/info`, {
        credentials: 'include'
      });

      const data = await response.json();
      if (data.code === 200) {
        return data.data;
      }
      return null;
    } catch (error) {
      console.error("获取用户详细信息失败:", error);
      return null;
    }
  },

  // 获取用户兑换记录
  async getUserPrizeExchanges() {
    try {
      const response = await fetch(`${API_BASE}/user/prize-exchanges`, {
        credentials: 'include'
      });

      const data = await response.json();
      if (data.code === 200) {
        return data.data;
      }
      return [];
    } catch (error) {
      console.error("获取兑换记录失败:", error);
      return [];
    }
  },

  // 检查是否为管理员
  isAdmin(user) {
    return user && user.role === 1;
  }
};

// 导航工具
export function updateNavForAuth() {
  return authApi.getCurrentUser().then(user => {
    const navLinks = document.getElementById('navLinks');
    if (!navLinks) return;

    if (user) {
      // 已登录，显示个人中心和登出
      const profileLink = document.createElement('a');
      profileLink.href = 'profile.html';
      profileLink.textContent = '个人中心';

      const logoutLink = document.createElement('a');
      logoutLink.href = '#';
      logoutLink.textContent = '登出';
      logoutLink.onclick = async (e) => {
        e.preventDefault();
        await authApi.logout();
        window.location.href = 'index.html';
      };

      // 添加到导航
      navLinks.appendChild(profileLink);
      navLinks.appendChild(logoutLink);

      // 如果是管理员，显示管理入口
      if (authApi.isAdmin(user)) {
        const adminLink = document.createElement('a');
        adminLink.href = 'admin-matches.html';
        adminLink.textContent = '管理后台';
        navLinks.appendChild(adminLink);
      }
    } else {
      // 未登录，显示登录链接
      const loginLink = document.createElement('a');
      loginLink.href = 'login.html';
      loginLink.textContent = '登录';
      navLinks.appendChild(loginLink);
    }
  });
}

// 检查登录状态的工具函数
export function requireAuth() {
  return authApi.getCurrentUser().then(user => {
    if (!user) {
      window.location.href = 'login.html?redirect=' + encodeURIComponent(window.location.href);
      return false;
    }
    return user;
  });
}

// 检查管理员权限
export function requireAdmin() {
  return authApi.getCurrentUser().then(user => {
    if (!user) {
      window.location.href = 'login.html?redirect=' + encodeURIComponent(window.location.href);
      return false;
    }
    if (!authApi.isAdmin(user)) {
      alert("需要管理员权限才能访问此页面");
      window.location.href = 'index.html';
      return false;
    }
    return true;
  });
}

// 格式化日期
export function formatDate(dateString) {
  if (!dateString) return '-';
  const date = new Date(dateString);
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
}

// 格式化状态
export function formatExchangeStatus(status) {
  const statusMap = {
    'pending': '待处理',
    'approved': '已批准',
    'rejected': '已拒绝',
    'completed': '已完成',
    'cancelled': '已取消'
  };
  return statusMap[status] || status;
}
