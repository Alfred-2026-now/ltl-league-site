const SERIES = [
  { key: "teamDelta", assetKey: "teamAssets", label: "队伍资产", color: "#6EE7F9" },
  { key: "leagueDelta", assetKey: "leagueAssets", label: "联盟资产", color: "#A78BFA" },
  { key: "playerDelta", assetKey: "playerAssets", label: "个人资产", color: "#FBBF24" }
];

export function buildChangeChartModel(rows, currentAssets = {}) {
  const safeRows = Array.isArray(rows) ? rows : [];
  const series = SERIES.map(seriesConfig => {
    let balance = Number(currentAssets[seriesConfig.assetKey] || 0);
    const values = new Array(safeRows.length);
    for (let index = safeRows.length - 1; index >= 0; index -= 1) {
      values[index] = balance;
      balance -= Number(safeRows[index]?.[seriesConfig.key] || 0);
    }
    return {
      ...seriesConfig,
      values
    };
  });
  const values = series.flatMap(item => item.values);
  const minValue = values.length ? Math.min(...values) : 0;
  const maxValue = values.length ? Math.max(...values) : 0;

  return {
    labels: safeRows.map(row => String(row?.date || "").slice(5) || "-"),
    minValue,
    maxValue,
    series
  };
}

function niceStep(maxAbs) {
  if (maxAbs <= 0) {
    return 1;
  }
  const rough = maxAbs / 4;
  const power = 10 ** Math.floor(Math.log10(rough));
  const normalized = rough / power;
  if (normalized <= 1) return power;
  if (normalized <= 2) return 2 * power;
  if (normalized <= 5) return 5 * power;
  return 10 * power;
}

function formatAxisValue(value) {
  return `${Number(value || 0).toLocaleString("zh-CN")}P`;
}

export function findNearestChartPoint(points, x, y, maxDistance = 12) {
  let nearest = null;
  let nearestDistance = maxDistance;
  (points || []).forEach(point => {
    const distance = Math.hypot(point.x - x, point.y - y);
    if (distance <= nearestDistance) {
      nearest = point;
      nearestDistance = distance;
    }
  });
  return nearest;
}

function ensureTooltip(canvas) {
  const parent = canvas.parentElement;
  if (!parent) {
    return null;
  }
  if (getComputedStyle(parent).position === "static") {
    parent.style.position = "relative";
  }
  let tooltip = parent.querySelector("[data-assets-chart-tooltip]");
  if (!tooltip) {
    tooltip = document.createElement("div");
    tooltip.dataset.assetsChartTooltip = "true";
    tooltip.style.cssText = [
      "position:absolute",
      "z-index:5",
      "display:none",
      "pointer-events:none",
      "padding:.45rem .6rem",
      "border:1px solid rgba(148, 163, 184, .35)",
      "border-radius:8px",
      "background:rgba(4, 12, 28, .94)",
      "box-shadow:0 12px 30px rgba(0, 0, 0, .28)",
      "color:#e5edf7",
      "font-size:.85rem",
      "line-height:1.45",
      "white-space:nowrap"
    ].join(";");
    parent.appendChild(tooltip);
  }
  return tooltip;
}

function bindTooltip(canvas) {
  if (canvas.dataset.assetsChartTooltipBound === "true") {
    return;
  }
  canvas.dataset.assetsChartTooltipBound = "true";
  canvas.addEventListener("mousemove", event => {
    const tooltip = ensureTooltip(canvas);
    if (!tooltip) {
      return;
    }
    const rect = canvas.getBoundingClientRect();
    const x = event.clientX - rect.left;
    const y = event.clientY - rect.top;
    const point = findNearestChartPoint(canvas.__assetChartPoints || [], x, y, 18);
    if (!point) {
      tooltip.style.display = "none";
      canvas.style.cursor = "default";
      return;
    }
    tooltip.innerHTML = `
      <div style="display:flex;align-items:center;gap:.4rem;">
        <span aria-hidden="true" style="width:9px;height:9px;border-radius:999px;background:${point.color};display:inline-block;"></span>
        <strong>${point.seriesLabel}</strong>
      </div>
      <div>${point.label}：${formatAxisValue(point.value)}</div>
    `;
    const tooltipWidth = tooltip.offsetWidth || 140;
    const left = Math.min(Math.max(point.x + 12, 8), rect.width - tooltipWidth - 8);
    const top = Math.max(point.y - 42, 8);
    tooltip.style.left = `${left}px`;
    tooltip.style.top = `${top}px`;
    tooltip.style.display = "block";
    canvas.style.cursor = "crosshair";
  });
  canvas.addEventListener("mouseleave", () => {
    const tooltip = ensureTooltip(canvas);
    if (tooltip) {
      tooltip.style.display = "none";
    }
    canvas.style.cursor = "default";
  });
}

export function renderChangeChart(canvas, rows, currentAssets = {}) {
  if (!canvas) {
    return;
  }
  bindTooltip(canvas);
  const model = buildChangeChartModel(rows, currentAssets);
  const ctx = canvas.getContext("2d");
  const rect = canvas.getBoundingClientRect();
  const width = Math.max(320, Math.round(rect.width || canvas.clientWidth || 720));
  const height = Math.max(240, Math.round(rect.height || canvas.clientHeight || 280));
  const ratio = window.devicePixelRatio || 1;
  canvas.width = Math.round(width * ratio);
  canvas.height = Math.round(height * ratio);
  ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
  ctx.clearRect(0, 0, width, height);

  const padding = { top: 22, right: 24, bottom: 42, left: 72 };
  const chartWidth = width - padding.left - padding.right;
  const chartHeight = height - padding.top - padding.bottom;
  const spread = model.maxValue - model.minValue;
  const step = niceStep(spread || model.maxValue);
  const upper = Math.ceil(model.maxValue / step) * step;
  const lower = Math.floor(model.minValue / step) * step;
  const range = upper - lower || 1;
  const points = [];

  const xFor = index => {
    if (model.labels.length <= 1) {
      return padding.left + chartWidth / 2;
    }
    return padding.left + (chartWidth * index) / (model.labels.length - 1);
  };
  const yFor = value => padding.top + ((upper - value) / range) * chartHeight;

  ctx.font = "12px system-ui, -apple-system, BlinkMacSystemFont, sans-serif";
  ctx.lineWidth = 1;
  ctx.strokeStyle = "rgba(148, 163, 184, 0.18)";
  ctx.fillStyle = "rgba(226, 232, 240, 0.72)";
  ctx.textAlign = "right";
  ctx.textBaseline = "middle";

  for (let tick = lower; tick <= upper; tick += step) {
    const y = yFor(tick);
    ctx.beginPath();
    ctx.moveTo(padding.left, y);
    ctx.lineTo(width - padding.right, y);
    ctx.stroke();
    ctx.fillText(formatAxisValue(tick), padding.left - 10, y);
  }

  const zeroY = yFor(0);
  ctx.strokeStyle = "rgba(226, 232, 240, 0.5)";
  ctx.beginPath();
  ctx.moveTo(padding.left, zeroY);
  ctx.lineTo(width - padding.right, zeroY);
  ctx.stroke();

  model.series.forEach(series => {
    if (!series.values.length) {
      return;
    }
    ctx.strokeStyle = series.color;
    ctx.lineWidth = 2.5;
    ctx.beginPath();
    series.values.forEach((value, index) => {
      const x = xFor(index);
      const y = yFor(value);
      if (index === 0) {
        ctx.moveTo(x, y);
      } else {
        ctx.lineTo(x, y);
      }
    });
    ctx.stroke();

    ctx.fillStyle = series.color;
    series.values.forEach((value, index) => {
      const x = xFor(index);
      const y = yFor(value);
      points.push({
        x,
        y,
        label: model.labels[index],
        seriesLabel: series.label,
        value,
        color: series.color
      });
      ctx.beginPath();
      ctx.arc(x, y, 3.5, 0, Math.PI * 2);
      ctx.fill();
    });
  });
  canvas.__assetChartPoints = points;

  ctx.fillStyle = "rgba(226, 232, 240, 0.68)";
  ctx.textAlign = "center";
  ctx.textBaseline = "top";
  const labelEvery = Math.max(1, Math.ceil(model.labels.length / 7));
  model.labels.forEach((label, index) => {
    if (index % labelEvery !== 0 && index !== model.labels.length - 1) {
      return;
    }
    ctx.fillText(label, xFor(index), height - padding.bottom + 14);
  });
}

export function renderChangeChartLegend(container) {
  if (!container) {
    return;
  }
  container.innerHTML = SERIES.map(series => `
    <span style="display:inline-flex;align-items:center;gap:.4rem;white-space:nowrap;">
      <span aria-hidden="true" style="width:10px;height:10px;border-radius:999px;background:${series.color};display:inline-block;"></span>
      ${series.label}
    </span>
  `).join("");
}
