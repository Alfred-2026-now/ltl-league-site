import * as api from "./api.js";

let prizes = [];

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

async function loadPrizes() {
  const loadingContainer = document.getElementById('loadingContainer');
  const prizeContainer = document.getElementById('prizeContainer');
  const emptyContainer = document.getElementById('emptyContainer');

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
            <button class="btn secondary" onclick="window.editPrize(${prize.id})">编辑</button>
            <button class="btn" style="background:#ff6b6b;color:white;" onclick="window.deletePrize(${prize.id})">删除</button>
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

function openModal(prize = null) {
  const modal = document.getElementById('prizeModal');
  const title = document.getElementById('modalTitle');
  const form = document.getElementById('prizeForm');
  const isActiveGroup = document.getElementById('isActiveGroup');

  form.reset();
  document.getElementById('imagePreview').classList.remove('show');

  if (prize) {
    title.textContent = '编辑奖品';
    document.getElementById('prizeId').value = prize.id;
    document.getElementById('prizeName').value = prize.name;
    document.getElementById('prizeDescription').value = prize.description || '';
    document.getElementById('prizeImageUrl').value = prize.imageUrl || '';
    document.getElementById('prizeCostPoints').value = prize.costPoints;
    document.getElementById('prizeStock').value = prize.stock;
    document.getElementById('prizeIsActive').value = prize.isActive;
    isActiveGroup.style.display = 'block';

    if (prize.imageUrl) {
      const preview = document.getElementById('imagePreview');
      preview.src = prize.imageUrl;
      preview.classList.add('show');
    }
  } else {
    title.textContent = '添加奖品';
    document.getElementById('prizeId').value = '';
    isActiveGroup.style.display = 'none';
  }

  modal.classList.add('show');
}

function closeModal() {
  document.getElementById('prizeModal').classList.remove('show');
}

async function savePrize(e) {
  e.preventDefault();

  const prizeId = document.getElementById('prizeId').value;
  const payload = {
    name: document.getElementById('prizeName').value.trim(),
    description: document.getElementById('prizeDescription').value.trim(),
    imageUrl: document.getElementById('prizeImageUrl').value.trim(),
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

    closeModal();
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

document.getElementById('addPrizeBtn').addEventListener('click', () => openModal());
document.getElementById('prizeForm').addEventListener('submit', savePrize);

document.getElementById('prizeImageUrl').addEventListener('input', (e) => {
  const url = e.target.value.trim();
  const preview = document.getElementById('imagePreview');

  if (url) {
    preview.src = url;
    preview.classList.add('show');
  } else {
    preview.classList.remove('show');
  }
});

document.getElementById('prizeModal').addEventListener('click', (e) => {
  if (e.target === document.getElementById('prizeModal')) {
    closeModal();
  }
});

window.editPrize = (id) => {
  const prize = prizes.find(p => p.id === id);
  if (prize) {
    openModal(prize);
  }
};

window.deletePrize = deletePrize;

loadPrizes();
