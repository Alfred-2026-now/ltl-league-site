import * as api from "./api.js";
import { getApiBase } from "../config/api.js";

let prizes = [];
let exchanges = [];
let currentStatus = "";

function showError(message) {
  const container = document.getElementById('errorContainer');
  container.innerHTML = `<div class="panel" style="padding:1rem;background:#fed7d7;color:#c53030;margin-bottom:1rem;">${message}</div>`;
  container.style.display = 'block';
  setTimeout(() => {
    container.style.display = 'none';
  }, 5000);
}

function showSuccess(message) {
  const container = document.getElementById('successContainer');
  container.innerHTML = `<div class="panel" style="padding:1rem;background:#c6f6d5;color:#2f855a;margin-bottom:1rem;">${message}</div>`;
  container.style.display = 'block';
  setTimeout(() => {
    container.style.display = 'none';
  }, 3000);
}

// 标签页切换
document.querySelectorAll('.tab-btn').forEach(btn => {
  btn.addEventListener('click', (e) => {
    const tabName = e.target.dataset.tab;

    document.querySelectorAll('.tab-btn').forEach(t => t.classList.remove('active'));
    e.target.classList.add('active');

    document.querySelectorAll('.tab-content').forEach(content => content.classList.remove('active'));
    document.getElementById(tabName + 'Tab').classList.add('active');

    if (tabName === 'prizes') {
      loadPrizes();
    } else {
      loadExchanges(currentStatus);
    }
  });
});

// 奖品管理功能
async function loadPrizes() {
  const loadingContainer = document.getElementById('loadingPrizes');
  const prizeContainer = document.getElementById('prizeContainer');
  const emptyContainer = document.getElementById('emptyPrizes');

  try {
    loadingContainer.style.display = 'block';
    prizes = await api.listAllPrizes();

    prizeContainer.innerHTML = '';
    loadingContainer.style.display = 'none';

    if (prizes.length === 0) {
      emptyContainer.style.display = 'block';
      return;
    }

    emptyContainer.style.display = 'none';

    prizes.forEach(prize => {
      const card = document.createElement('div');
      card.className = 'prize-card';
      if (prize.isActive === 0) {
        card.classList.add('inactive');
      }

      const stockClass = prize.stock <= 3 ? 'low' : '';
      const statusClass = prize.isActive === 0 ? 'inactive' : '';

      card.innerHTML = `
        <img src="${prize.imageUrl || 'https://via.placeholder.com/400x300?text=No+Image'}"
             alt="${prize.name}"
             class="prize-image"
             onerror="this.src='https://via.placeholder.com/400x300?text=No+Image'" />
        <div class="prize-body">
          <div class="prize-header">
            <h3 class="prize-name">${prize.name}</h3>
            <span class="prize-status ${statusClass}">${prize.isActive === 1 ? '启用' : '禁用'}</span>
          </div>
          ${prize.description ? `<p class="prize-description">${prize.description}</p>` : ''}
          <div class="prize-meta">
            <span class="prize-cost">${prize.costPoints} 积分</span>
            <span class="prize-stock ${stockClass}">库存: ${prize.stock}</span>
          </div>
          <div class="prize-actions">
            <button class="btn btn-secondary" onclick="window.editPrize(${prize.id})">编辑</button>
            <button class="btn btn-danger" onclick="window.deletePrize(${prize.id})">删除</button>
          </div>
        </div>
      `;

      prizeContainer.appendChild(card);
    });

  } catch (error) {
    loadingContainer.style.display = 'none';
    showError('加载奖品失败：' + error.message);
  }
}

function openPrizeModal(prize = null) {
  const modal = document.getElementById('prizeModal');
  const title = document.getElementById('modalTitle');
  const form = document.getElementById('prizeForm');
  const isActiveGroup = document.getElementById('isActiveGroup');
  const preview = document.getElementById('imagePreview');

  form.reset();
  preview.classList.remove('show');
  preview.src = '';
  document.getElementById('prizeImageUrl').value = '';

  if (prize) {
    title.textContent = '编辑奖品';
    document.getElementById('prizeId').value = prize.id;
    document.getElementById('prizeName').value = prize.name;
    document.getElementById('prizeDescription').value = prize.description || '';
    document.getElementById('prizeCostPoints').value = prize.costPoints;
    document.getElementById('prizeStock').value = prize.stock;
    document.getElementById('prizeIsActive').value = prize.isActive;
    isActiveGroup.style.display = 'block';

    // 延迟设置图片预览，确保在表单重置之后
    if (prize.imageUrl) {
      document.getElementById('prizeImageUrl').value = prize.imageUrl;
      preview.src = prize.imageUrl;
      preview.onload = () => {
        preview.classList.add('show');
      };
      preview.onerror = () => {
        console.error('图片加载失败:', prize.imageUrl);
      };
    }
  } else {
    title.textContent = '添加奖品';
    document.getElementById('prizeId').value = '';
    isActiveGroup.style.display = 'none';
  }

  modal.classList.add('show');
}

function closePrizeModal() {
  document.getElementById('prizeModal').classList.remove('show');
}

async function savePrize(e) {
  e.preventDefault();

  const prizeId = document.getElementById('prizeId').value;
  const imageUrl = document.getElementById('prizeImageUrl').value;

  const payload = {
    name: document.getElementById('prizeName').value.trim(),
    description: document.getElementById('prizeDescription').value.trim(),
    imageUrl: imageUrl,
    costPoints: parseInt(document.getElementById('prizeCostPoints').value),
    stock: parseInt(document.getElementById('prizeStock').value)
  };

  if (prizeId) {
    payload.isActive = parseInt(document.getElementById('prizeIsActive').value);
  }

  try {
    if (prizeId) {
      await api.updatePrize(parseInt(prizeId), payload);
      showSuccess('奖品更新成功');
    } else {
      await api.createPrize(payload);
      showSuccess('奖品添加成功');
    }

    closePrizeModal();
    loadPrizes();

  } catch (error) {
    showError('保存失败：' + error.message);
  }
}

async function deletePrize(id) {
  if (!confirm('确定要删除这个奖品吗？')) {
    return;
  }

  try {
    await api.deletePrize(id);
    showSuccess('奖品删除成功');
    loadPrizes();
  } catch (error) {
    showError('删除失败：' + error.message);
  }
}

// 图片上传功能
const uploadArea = document.getElementById('uploadArea');
const imageFile = document.getElementById('imageFile');
const imagePreview = document.getElementById('imagePreview');
const prizeImageUrl = document.getElementById('prizeImageUrl');

uploadArea.addEventListener('click', () => imageFile.click());

uploadArea.addEventListener('dragover', (e) => {
  e.preventDefault();
  uploadArea.classList.add('dragover');
});

uploadArea.addEventListener('dragleave', () => {
  uploadArea.classList.remove('dragover');
});

uploadArea.addEventListener('drop', (e) => {
  e.preventDefault();
  uploadArea.classList.remove('dragover');
  const files = e.dataTransfer.files;
  if (files.length > 0) {
    handleImageUpload(files[0]);
  }
});

imageFile.addEventListener('change', (e) => {
  if (e.target.files.length > 0) {
    handleImageUpload(e.target.files[0]);
  }
});

async function handleImageUpload(file) {
  if (!file.type.startsWith('image/')) {
    showError('请选择图片文件');
    return;
  }

  if (file.size > 5 * 1024 * 1024) {
    showError('图片大小不能超过5MB');
    return;
  }

  // 显示本地预览
  const reader = new FileReader();
  reader.onload = (e) => {
    imagePreview.src = e.target.result;
    imagePreview.classList.add('show');
  };
  reader.readAsDataURL(file);

  try {
    showSuccess('图片上传中...');

    const formData = new FormData();
    formData.append('file', file);

    // 上传到后端
    const response = await fetch(`${getApiBase()}/prizes/upload`, {
      method: 'POST',
      body: formData
    });

    if (!response.ok) {
      throw new Error('上传失败');
    }

    const data = await response.json();
    if (data.code !== 200) {
      throw new Error(data.message || '上传失败');
    }

    prizeImageUrl.value = data.data.url;
    showSuccess('图片上传成功');

  } catch (error) {
    showError('图片上传失败：' + error.message);
    imagePreview.classList.remove('show');
  }
}

// 兑换流水功能
async function loadExchanges(status = "") {
  const loadingContainer = document.getElementById('loadingExchanges');
  const exchangeContainer = document.getElementById('exchangeContainer');
  const emptyContainer = document.getElementById('emptyExchanges');
  const totalCount = document.getElementById('totalCount');

  try {
    loadingContainer.style.display = 'block';
    exchanges = await api.listExchanges({ status, limit: 100 });

    exchangeContainer.innerHTML = '';
    loadingContainer.style.display = 'none';

    totalCount.textContent = `共 ${exchanges.length} 条记录`;

    if (exchanges.length === 0) {
      emptyContainer.style.display = 'block';
      return;
    }

    emptyContainer.style.display = 'none';

    exchanges.forEach(exchange => {
      const card = document.createElement('div');
      card.className = 'exchange-card';

      const statusClass = {
        'pending': 'status-pending',
        'completed': 'status-completed',
        'cancelled': 'status-cancelled'
      }[exchange.status] || '';

      const statusText = {
        'pending': '待处理',
        'completed': '已完成',
        'cancelled': '已取消'
      }[exchange.status] || exchange.status;

      card.innerHTML = `
        <div class="exchange-card-header">
          <span class="exchange-id">#${exchange.id}</span>
          <span class="status-badge ${statusClass}">${statusText}</span>
        </div>
        <div class="exchange-prize">${exchange.prizeName}</div>
        <div class="exchange-player">选手：${exchange.playerName}</div>
        <div class="exchange-meta">
          <span>积分：${exchange.costPoints}</span>
          <span>${formatTime(exchange.createdAt)}</span>
        </div>
        ${exchange.status === 'pending' ? `
          <div class="action-buttons">
            <button class="btn btn-success" onclick="window.processExchange(${exchange.id})">完成兑换</button>
            <button class="btn btn-danger" onclick="window.cancelExchange(${exchange.id})">取消</button>
          </div>
        ` : exchange.status === 'completed' ? `
          <div class="exchange-detail">
            <div class="detail-row">
              <span class="detail-label">处理人</span>
              <span class="detail-value">${exchange.processedBy || '管理员'}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">处理时间</span>
              <span class="detail-value">${formatTime(exchange.processedAt)}</span>
            </div>
          </div>
        ` : ''}
      `;

      exchangeContainer.appendChild(card);
    });

  } catch (error) {
    loadingContainer.style.display = 'none';
    showError('加载失败：' + error.message);
  }
}

function formatTime(timeStr) {
  if (!timeStr) return '';
  const date = new Date(timeStr);
  const now = new Date();
  const diff = now - date;

  if (diff < 60000) {
    return '刚刚';
  } else if (diff < 3600000) {
    return `${Math.floor(diff / 60000)} 分钟前`;
  } else if (diff < 86400000) {
    return `${Math.floor(diff / 3600000)} 小时前`;
  } else {
    return date.toLocaleDateString('zh-CN', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    });
  }
}

async function processExchange(id) {
  if (!confirm('确认已完成兑换？')) {
    return;
  }

  try {
    await api.processExchange(id, 'admin');
    showSuccess('兑换已完成');
    loadExchanges(currentStatus);
  } catch (error) {
    showError('操作失败：' + error.message);
  }
}

async function cancelExchange(id) {
  const reason = prompt('请输入取消原因：');
  if (!reason) {
    return;
  }

  try {
    await api.cancelExchange(id, reason);
    showSuccess('兑换已取消');
    loadExchanges(currentStatus);
  } catch (error) {
    showError('操作失败：' + error.message);
  }
}

// 事件绑定
document.addEventListener('DOMContentLoaded', () => {
  loadPrizes();

  document.getElementById('addPrizeBtn').addEventListener('click', () => openPrizeModal());
  document.getElementById('prizeForm').addEventListener('submit', savePrize);

  // 兑换流水筛选
  document.querySelectorAll('.filter-tab').forEach(tab => {
    tab.addEventListener('click', (e) => {
      document.querySelectorAll('.filter-tab').forEach(t => t.classList.remove('active'));
      e.target.classList.add('active');
      currentStatus = e.target.dataset.status;
      loadExchanges(currentStatus);
    });
  });

  document.getElementById('refreshBtn').addEventListener('click', () => {
    loadExchanges(currentStatus);
  });
});

// 全局函数暴露
window.editPrize = (id) => {
  const prize = prizes.find(p => p.id === id);
  if (prize) {
    openPrizeModal(prize);
  }
};

window.deletePrize = deletePrize;
window.processExchange = processExchange;
window.cancelExchange = cancelExchange;
window.closePrizeModal = closePrizeModal;
