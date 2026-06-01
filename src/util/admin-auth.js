// 管理员权限验证
// 此脚本应在管理页面加载时首先执行

import { authApi, requireAdmin } from './auth.js';

// 立即执行管理员权限验证
requireAdmin().then(isAdmin => {
  if (isAdmin) {
    console.log('管理员权限验证通过');
    // 验证通过，移除加载遮罩（如果有的话）
    const loadingMask = document.getElementById('adminLoadingMask');
    if (loadingMask) {
      loadingMask.style.display = 'none';
    }
  }
});

// 导出供其他地方使用
export { authApi };
