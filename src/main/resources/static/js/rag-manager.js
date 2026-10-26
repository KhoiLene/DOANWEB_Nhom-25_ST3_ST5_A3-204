(() => {
  const root = document.getElementById('ragManager');
  if (!root) return;

  const api = '/api/admin/assistant-knowledge';
  const roleInfo = {
    customer: { label: 'Khách hàng', color: '#58cbff', position: [-0.62, 0.48, 0.40] },
    'store-staff': { label: 'Cửa hàng', color: '#ffba5c', position: [0.62, 0.48, -0.40] },
    warehouse: { label: 'Kho', color: '#8be09a', position: [-0.62, -0.48, -0.40] },
    admin: { label: 'Admin', color: '#c69aff', position: [0.62, -0.48, 0.40] }
  };
  const canvas = document.getElementById('ragGlobe');
  const ctx = canvas.getContext('2d');
  const tooltip = document.getElementById('ragGlobeTooltip');
  const sourceList = document.getElementById('ragSourceList');
  const status = document.getElementById('ragStatus');
  const form = document.getElementById('ragSourceForm');
  const formId = document.getElementById('ragSourceId');
  const formRole = document.getElementById('ragSourceRole');
  const formName = document.getElementById('ragSourceName');
  const formContent = document.getElementById('ragSourceContent');
  const formActive = document.getElementById('ragSourceActive');
  let sources = [];
  let activeFilter = 'all';
  let selectedId = null;
  let graphNodes = [];
  let graphEdges = [];
  let rotation = { x: -0.22, y: 0.15 };
  let dragging = false;
  let didDrag = false;
  let suppressClick = false;
  let lastPointer = null;
  let hoveredNode = null;
  let pixelRatio = 1;

  const normalize = text => text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase();
  const escapeRole = role => roleInfo[role]?.label || role;

  async function request(path = '', options = {}) {
    const response = await fetch(`${api}${path}`, {
      ...options,
      headers: { Accept: 'application/json', ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...(options.headers || {}) }
    });
    if (response.status === 204) return null;
    const result = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(result.error || 'Không thể cập nhật kho tri thức.');
    return result;
  }

  function renderMetrics() {
    document.getElementById('ragSourceCount').textContent = sources.length;
    document.getElementById('ragActiveCount').textContent = sources.filter(source => source.active).length;
    document.getElementById('ragRoleCount').textContent = new Set(sources.filter(source => source.active).map(source => source.roleKey)).size;
  }

  function renderSources() {
    sourceList.replaceChildren();
    const visible = sources.filter(source => activeFilter === 'all' || source.roleKey === activeFilter);
    if (!visible.length) {
      const empty = document.createElement('p');
      empty.className = 'rag-manager__status';
      empty.textContent = 'Chưa có nguồn trong phạm vi này. Thêm tài liệu để bật chatbot cho role.';
      sourceList.append(empty);
      return;
    }
    visible.forEach(source => {
      const row = document.createElement('article');
      row.className = `rag-source-row${source.id === selectedId ? ' is-selected' : ''}`;
      row.dataset.sourceId = source.id;
      const details = document.createElement('div');
      const title = document.createElement('div');
      title.className = 'rag-source-row__title';
      title.textContent = source.title;
      const meta = document.createElement('div');
      meta.className = 'rag-source-row__meta';
      const role = document.createElement('span');
      role.className = 'rag-source-row__role';
      role.textContent = escapeRole(source.roleKey);
      const state = document.createElement('span');
      state.className = `rag-source-row__status${source.active ? '' : ' is-off'}`;
      state.textContent = source.active ? 'ĐANG DÙNG' : 'ĐÃ TẮT';
      meta.append(role, state);
      details.append(title, meta);
      const actions = document.createElement('div');
      actions.className = 'rag-source-row__actions';
      const toggle = document.createElement('button');
      toggle.type = 'button';
      toggle.dataset.action = 'toggle';
      toggle.textContent = source.active ? 'Tắt' : 'Bật';
      toggle.setAttribute('aria-label', `${source.active ? 'Tắt' : 'Bật'} ${source.title}`);
      actions.append(toggle);
      row.append(details, actions);
      row.addEventListener('click', event => {
        if (event.target.closest('[data-action]')) return;
        editSource(source);
      });
      toggle.addEventListener('click', async event => {
        event.stopPropagation();
        try {
          status.textContent = 'Đang cập nhật trạng thái nguồn…';
          const updated = await request(`/${source.id}/toggle`, { method: 'PATCH' });
          sources = sources.map(item => item.id === updated.id ? updated : item);
          if (selectedId === updated.id) editSource(updated);
          refreshView('Đã cập nhật trạng thái nguồn tri thức.');
        } catch (error) { status.textContent = error.message; }
      });
      sourceList.append(row);
    });
  }

  function editSource(source) {
    selectedId = source.id;
    formId.value = source.id;
    formRole.value = source.roleKey;
    formName.value = source.title;
    formContent.value = source.content;
    formActive.checked = source.active;
    document.getElementById('ragEditorTitle').textContent = 'Chỉnh sửa nguồn';
    document.getElementById('ragDeleteSource').hidden = false;
    renderSources();
    document.getElementById('ragEditorCard').scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }

  function resetForm() {
    selectedId = null;
    form.reset();
    formId.value = '';
    formActive.checked = true;
    formRole.value = activeFilter === 'all' ? 'customer' : activeFilter;
    document.getElementById('ragEditorTitle').textContent = 'Nguồn mới';
    document.getElementById('ragDeleteSource').hidden = true;
    renderSources();
  }

  function topConcepts(source) {
    const stop = new Set(['huong', 'dan', 'techtra', 'beauty', 'nguon', 'trong', 'duoc', 'khi', 'cho', 'mot', 'nhung', 'theo', 'khong', 'can', 'cua', 'hang', 'nhan', 'vien', 'khach', 'admin', 'role', 'source']);
    const counts = new Map();
    const words = normalize(`${source.title} ${source.content}`)
      .split(/[^a-z0-9]+/).filter(word => word.length >= 4 && !stop.has(word));
    words.forEach(word => counts.set(word, (counts.get(word) || 0) + 1));
    return [...counts.entries()].sort((a, b) => b[1] - a[1]).slice(0, 2).map(([word]) => word);
  }

  function normalize3(vector) {
    const length = Math.hypot(...vector) || 1;
    return vector.map(value => value / length);
  }

  function add(a, b) { return a.map((value, index) => value + b[index]); }
  function scale(vector, amount) { return vector.map(value => value * amount); }
  function cross(a, b) { return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]; }

  function createGraph() {
    graphNodes = [{ key: 'core', type: 'core', label: 'RAG', position: [0, 0, 0] }];
    graphEdges = [];
    const hubByRole = {};
    Object.entries(roleInfo).forEach(([role, info]) => {
      const key = `role:${role}`;
      hubByRole[role] = key;
      graphNodes.push({ key, type: 'role', role, label: info.label, position: normalize3(info.position) });
      graphEdges.push({ from: 'core', to: key, role });
    });

    const conceptByRole = new Map();
    for (const [role, info] of Object.entries(roleInfo)) {
      const roleSources = sources.filter(source => source.roleKey === role).slice(0, 12);
      const center = normalize3(info.position);
      const tangent = normalize3(cross(center, [0, 1, 0]));
      const tangent2 = normalize3(cross(center, tangent));
      roleSources.forEach((source, index) => {
        const angle = index * 2.399963229728653;
        const offset = add(scale(tangent, Math.cos(angle) * 0.34), scale(tangent2, Math.sin(angle) * 0.34));
        const docPosition = add(scale(center, 0.86), offset);
        const docKey = `source:${source.id}`;
        graphNodes.push({ key: docKey, type: 'source', sourceId: source.id, role, label: source.title, position: docPosition, active: source.active });
        graphEdges.push({ from: hubByRole[role], to: docKey, role });

        topConcepts(source).forEach((concept, conceptIndex) => {
          const conceptAngle = angle + conceptIndex * Math.PI + 0.8;
          const conceptOffset = add(scale(tangent, Math.cos(conceptAngle) * 0.15), scale(tangent2, Math.sin(conceptAngle) * 0.15));
          const conceptPosition = add(scale(normalize3(docPosition), 1.02), conceptOffset);
          const conceptKey = `concept:${role}:${concept}`;
          if (!graphNodes.some(node => node.key === conceptKey)) {
            graphNodes.push({ key: conceptKey, type: 'concept', role, label: concept, position: conceptPosition });
          }
          graphEdges.push({ from: docKey, to: conceptKey, role });
          const previous = conceptByRole.get(`${role}:${concept}`);
          if (previous && previous !== docKey) graphEdges.push({ from: previous, to: conceptKey, role, faint: true });
          conceptByRole.set(`${role}:${concept}`, docKey);
        });
      });
    }
  }

  function resizeCanvas() {
    const bounds = canvas.getBoundingClientRect();
    pixelRatio = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = Math.max(1, Math.floor(bounds.width * pixelRatio));
    canvas.height = Math.max(1, Math.floor(bounds.height * pixelRatio));
    ctx.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
  }

  function project(position, width, height) {
    let [x, y, z] = position;
    const cosY = Math.cos(rotation.y), sinY = Math.sin(rotation.y);
    [x, z] = [x * cosY - z * sinY, x * sinY + z * cosY];
    const cosX = Math.cos(rotation.x), sinX = Math.sin(rotation.x);
    [y, z] = [y * cosX - z * sinX, y * sinX + z * cosX];
    const perspective = 1.65 / (1.65 - z * 0.22);
    return { x: width / 2 + x * width * 0.31 * perspective, y: height / 2 + y * height * 0.39 * perspective, z, perspective };
  }

  function drawFrame(time) {
    const width = canvas.clientWidth;
    const height = canvas.clientHeight;
    if (!width || !height) { requestAnimationFrame(drawFrame); return; }
    ctx.clearRect(0, 0, width, height);
    const projected = new Map(graphNodes.map(node => [node.key, { node, ...project(node.position, width, height) }]));

    graphEdges.forEach((edge, index) => {
      const from = projected.get(edge.from), to = projected.get(edge.to);
      if (!from || !to) return;
      const color = roleInfo[edge.role]?.color || '#9ab7ff';
      const depth = Math.max(0.10, Math.min(1, (from.z + to.z + 2) / 3));
      ctx.beginPath();
      ctx.moveTo(from.x, from.y);
      ctx.lineTo(to.x, to.y);
      ctx.strokeStyle = `${color}${edge.faint ? '22' : '70'}`;
      ctx.lineWidth = edge.faint ? 0.7 : 0.85 + depth * 0.8;
      ctx.stroke();
      if (!edge.faint && index % 3 === 0) {
        const progress = (time * 0.00008 + index * 0.17) % 1;
        const dotX = from.x + (to.x - from.x) * progress;
        const dotY = from.y + (to.y - from.y) * progress;
        ctx.beginPath();
        ctx.arc(dotX, dotY, 1.2 + depth, 0, Math.PI * 2);
        ctx.fillStyle = `${color}dd`;
        ctx.fill();
      }
    });

    const sortedNodes = [...projected.values()].sort((a, b) => a.z - b.z);
    sortedNodes.forEach(item => {
      const { node, x, y, z } = item;
      const roleColor = roleInfo[node.role]?.color || '#eff7ff';
      const depth = Math.max(0.25, Math.min(1, (z + 1.3) / 2.3));
      const isSelected = node.type === 'source' && node.sourceId === selectedId;
      const radius = node.type === 'core' ? 10 : node.type === 'role' ? 8 : node.type === 'source' ? (isSelected ? 6 : 4.6) : 2.4;
      ctx.beginPath();
      ctx.arc(x, y, radius + (isSelected ? 2 : 0), 0, Math.PI * 2);
      ctx.fillStyle = node.type === 'core' ? '#eff8ff' : node.active === false ? '#728096' : roleColor;
      ctx.globalAlpha = node.type === 'concept' ? 0.42 + depth * 0.48 : 0.55 + depth * 0.45;
      ctx.shadowBlur = node.type === 'concept' ? 6 : 14;
      ctx.shadowColor = node.type === 'core' ? '#d2edff' : roleColor;
      ctx.fill();
      ctx.globalAlpha = 1;
      ctx.shadowBlur = 0;
      if (node.type === 'core' || node.type === 'role' || node.type === 'source') {
        ctx.font = `${node.type === 'core' ? '700 12px' : node.type === 'role' ? '700 10px' : '500 9px'} system-ui, sans-serif`;
        ctx.textAlign = 'center';
        ctx.fillStyle = node.type === 'core' ? '#fff' : '#e6efff';
        const label = node.label.length > 22 ? `${node.label.slice(0, 20)}…` : node.label;
        ctx.fillText(label, x, y - radius - 6);
      }
      item.radius = radius + (isSelected ? 3 : 0);
    });
    canvas.__projected = sortedNodes;
    requestAnimationFrame(drawFrame);
  }

  function setFilter(filter) {
    activeFilter = filter;
    document.querySelectorAll('.rag-role-filter button').forEach(button => button.classList.toggle('is-active', button.dataset.filter === filter));
    renderSources();
    if (filter !== 'all') status.textContent = `Đang xem phạm vi ${escapeRole(filter)}. Bot của role này chỉ truy xuất các nguồn đang bật.`;
  }

  function refreshView(message = '') {
    renderMetrics();
    renderSources();
    createGraph();
    if (message) status.textContent = message;
  }

  document.querySelectorAll('.rag-role-filter button').forEach(button => {
    button.addEventListener('click', () => setFilter(button.dataset.filter));
  });
  document.getElementById('ragNewSource').addEventListener('click', () => {
    resetForm();
    document.getElementById('ragEditorCard').scrollIntoView({ behavior: 'smooth', block: 'start' });
    formName.focus();
  });
  document.getElementById('ragResetForm').addEventListener('click', resetForm);
  document.getElementById('ragImportFile').addEventListener('change', async event => {
    const file = event.target.files?.[0];
    if (!file) return;
    if (file.size > 100_000) { status.textContent = 'Tệp tối đa 100 KB.'; event.target.value = ''; return; }
    formContent.value = await file.text();
    if (!formName.value) formName.value = file.name.replace(/\.(md|txt)$/i, '');
    status.textContent = `Đã đọc ${file.name}. Nhấn Lưu nguồn để đưa vào kho tri thức.`;
  });

  form.addEventListener('submit', async event => {
    event.preventDefault();
    const payload = {
      roleKey: formRole.value,
      title: formName.value.trim(),
      content: formContent.value.trim(),
      active: formActive.checked
    };
    try {
      status.textContent = 'Đang lưu tài liệu vào PostgreSQL…';
      const saved = formId.value
        ? await request(`/${formId.value}`, { method: 'PUT', body: JSON.stringify(payload) })
        : await request('', { method: 'POST', body: JSON.stringify(payload) });
      const existing = sources.some(source => source.id === saved.id);
      sources = existing ? sources.map(source => source.id === saved.id ? saved : source) : [saved, ...sources];
      selectedId = saved.id;
      refreshView('Nguồn đã lưu. Chatbot chỉ dùng nguồn này trong role được chọn.');
      editSource(saved);
    } catch (error) { status.textContent = error.message; }
  });

  document.getElementById('ragDeleteSource').addEventListener('click', async () => {
    if (!formId.value || !window.confirm('Xóa nguồn tri thức này khỏi RAG?')) return;
    try {
      await request(`/${formId.value}`, { method: 'DELETE' });
      sources = sources.filter(source => String(source.id) !== formId.value);
      resetForm();
      refreshView('Đã xóa nguồn tri thức.');
    } catch (error) { status.textContent = error.message; }
  });

  canvas.addEventListener('pointerdown', event => {
    dragging = true;
    didDrag = false;
    lastPointer = { x: event.clientX, y: event.clientY };
    canvas.classList.add('is-dragging');
    canvas.setPointerCapture(event.pointerId);
    tooltip.hidden = true;
  });
  canvas.addEventListener('pointermove', event => {
    const bounds = canvas.getBoundingClientRect();
    if (dragging && lastPointer) {
      if (Math.hypot(event.clientX - lastPointer.x, event.clientY - lastPointer.y) > 3) didDrag = true;
      rotation.y += (event.clientX - lastPointer.x) * 0.006;
      rotation.x = Math.max(-1.2, Math.min(1.2, rotation.x + (event.clientY - lastPointer.y) * 0.005));
      lastPointer = { x: event.clientX, y: event.clientY };
      return;
    }
    const x = event.clientX - bounds.left, y = event.clientY - bounds.top;
    hoveredNode = (canvas.__projected || []).slice().reverse().find(item => Math.hypot(item.x - x, item.y - y) < Math.max(9, item.radius + 4)) || null;
    if (hoveredNode) {
      tooltip.textContent = hoveredNode.node.type === 'source'
        ? `${escapeRole(hoveredNode.node.role)} · ${hoveredNode.node.label}`
        : hoveredNode.node.type === 'role' ? `Role: ${hoveredNode.node.label}` : hoveredNode.node.label;
      tooltip.hidden = false;
      tooltip.style.left = `${Math.min(bounds.width - 245, Math.max(8, x + 14))}px`;
      tooltip.style.top = `${Math.min(bounds.height - 45, Math.max(8, y + 12))}px`;
    } else tooltip.hidden = true;
  });
  function endDrag() { suppressClick = didDrag; dragging = false; lastPointer = null; canvas.classList.remove('is-dragging'); }
  canvas.addEventListener('pointerup', endDrag);
  canvas.addEventListener('pointercancel', endDrag);
  canvas.addEventListener('click', event => {
    if (suppressClick) { suppressClick = false; return; }
    const bounds = canvas.getBoundingClientRect();
    const x = event.clientX - bounds.left, y = event.clientY - bounds.top;
    const clicked = (canvas.__projected || []).slice().reverse()
      .find(item => Math.hypot(item.x - x, item.y - y) < Math.max(10, item.radius + 5));
    if (!clicked) return;
    const source = sources.find(item => item.id === clicked.node.sourceId);
    if (source) editSource(source);
    else if (clicked.node.role) setFilter(clicked.node.role);
  });

  const resizeObserver = new ResizeObserver(resizeCanvas);
  resizeObserver.observe(canvas);
  resizeCanvas();
  requestAnimationFrame(drawFrame);
  request().then(data => {
    sources = data;
    refreshView(`Đã tải ${sources.length} nguồn. Ollama là tùy chọn; RAG vẫn trả lời từ tài liệu khi mô hình cục bộ chưa cài.`);
  }).catch(error => {
    status.textContent = error.message;
    document.getElementById('ragSourceCount').textContent = '0';
    document.getElementById('ragActiveCount').textContent = '0';
  });
})();
