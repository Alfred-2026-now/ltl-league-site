import * as api from "./api.js";

let exchanges = [];
let currentStatus = "";

function showSuccess(message) {
  const container = document.getElementById('successContainer');
  if (!container) {
    alert(message);
    return;
  }
  container.innerHTML = `<div class="panel" style="padding:1rem;background:#c6f6d5;color:#2f855a;margin-bottom:1rem;">${message}</div>`;
  container.style.display = 'block';
  setTimeout(() => {
    container.style.display = 'none';
  }, 3000);
}

async function loadExchanges(status = "") {
  const loadingContainer = document.getElementById('loadingContainer');
  const exchangeContainer = document.getElementById('exchangeContainer');
  const emptyContainer = document.getElementById('emptyContainer');
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
            <button class="btn primary" onclick="window.processExchange(${exchange.id})">完成兑换</button>
            <button class="btn secondary" style="background:#ff6b6b;color:white;" onclick="window.cancelExchange(${exchange.id})">取消</button>
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
    alert('加载失败：' + error.message);
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
    alert('操作失败：' + error.message);
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
    alert('操作失败：' + error.message);
  }
}

document.addEventListener('DOMContentLoaded', () => {
  loadExchanges();

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

window.processExchange = processExchange;
window.cancelExchange = cancelExchange;
