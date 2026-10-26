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
  let graphData = { nodes: [], edges: [], vectorAvailable: false, status: 'Đang tải embedding…' };
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
    document.getElementById('ragIndexedCount').textContent = sources.filter(source => source.active && source.indexStatus === 'INDEXED').length;
    document.getElementById('ragEdgeCount').textContent = graphData.edges.length;
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
      const indexState = document.createElement('span');
      indexState.className = `rag-source-row__index rag-source-row__index--${String(source.indexStatus || 'PENDING').toLowerCase()}`;
      indexState.textContent = `${source.indexStatus || 'PENDING'} · ${source.indexedChunkCount || 0} đoạn`;
      meta.append(role, state, indexState);
      details.append(title, meta);
      const actions = document.createElement('div');
      actions.className = 'rag-source-row__actions';
      const toggle = document.createElement('button');
      toggle.type = 'button';
      toggle.dataset.action = 'toggle';
      toggle.textContent = source.active ? 'Tắt' : 'Bật';
      toggle.setAttribute('aria-label', `${source.active ? 'Tắt' : 'Bật'} ${source.title}`);
      const reindex = document.createElement('button');
      reindex.type = 'button';
      reindex.dataset.action = 'reindex';
      reindex.textContent = 'Index lại';
      reindex.disabled = !source.active;
      reindex.setAttribute('aria-label', `Lập chỉ mục Gemini cho ${source.title}`);
      actions.append(toggle, reindex);
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
      reindex.addEventListener('click', async event => {
        event.stopPropagation();
        try {
          reindex.disabled = true;
          status.textContent = `Đang lập chỉ mục Gemini: ${source.title}…`;
          await request(`/${source.id}/reindex`, { method: 'POST' });
          await loadData('Đã yêu cầu lập chỉ mục. Trạng thái sẽ tự cập nhật khi Gemini hoàn tất.');
        } catch (error) { status.textContent = error.message; }
        finally { reindex.disabled = !source.active; }
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

  function normalize3(vector) {
    const length = Math.hypot(...vector) || 1;
    return vector.map(value => value / length);
  }

  function add(a, b) { return a.map((value, index) => value + b[index]); }
  function scale(vector, amount) { return vector.map(value => value * amount); }
  function cross(a, b) { return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]; }

  function createGraph() {
    graphNodes = [{ key: 'core', type: 'core', label: 'Gemini', position: [0, 0, 0] }];
    graphEdges = [];
    const hubByRole = {};
    Object.entries(roleInfo).forEach(([role, info]) => {
      const key = `role:${role}`;
      hubByRole[role] = key;
      graphNodes.push({ key, type: 'role', role, label: info.label, position: normalize3(info.position) });
      graphEdges.push({ from: 'core', to: key, role });
    });

    for (const [role, info] of Object.entries(roleInfo)) {
      const roleSources = graphData.nodes.filter(source => source.roleKey === role).slice(0, 20);
      const center = normalize3(info.position);
      const tangent = normalize3(cross(center, [0, 1, 0]));
      const tangent2 = normalize3(cross(center, tangent));
      roleSources.forEach((source, index) => {
        const angle = index * 2.399963229728653;
        const ring = 0.20 + Math.sqrt(index + 1) * 0.11;
        const offset = add(scale(tangent, Math.cos(angle) * ring), scale(tangent2, Math.sin(angle) * ring));
        const docPosition = add(scale(center, 0.84), offset);
        const docKey = `source:${source.id}`;
        graphNodes.push({ key: docKey, type: 'source', sourceId: source.id, role, label: source.title,
          position: docPosition, active: source.active, indexStatus: source.indexStatus, indexedChunkCount: source.indexedChunkCount });
        graphEdges.push({ from: hubByRole[role], to: docKey, role });
      });
    }
    const visible = new Set(graphNodes.filter(node => node.type === 'source').map(node => String(node.sourceId)));
    graphData.edges.forEach(edge => {
      if (!visible.has(String(edge.from)) || !visible.has(String(edge.to))) return;
      graphEdges.push({ from: `source:${edge.from}`, to: `source:${edge.to}`, role: edge.roleKey, type: 'embedding' });
    });
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
      ctx.strokeStyle = `${color}${edge.type === 'embedding' ? 'a0' : '24'}`;
      ctx.lineWidth = edge.type === 'embedding' ? 1.15 + depth * 0.8 : 0.65;
      ctx.stroke();
      if (edge.type === 'embedding' && index % 2 === 0) {
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
      const radius = node.type === 'core' ? 10 : node.type === 'role' ? 8 : (isSelected ? 6 : 4.6);
      ctx.beginPath();
      ctx.arc(x, y, radius + (isSelected ? 2 : 0), 0, Math.PI * 2);
      ctx.fillStyle = node.type === 'core' ? '#eff8ff' : node.active === false ? '#728096' : roleColor;
      ctx.globalAlpha = 0.55 + depth * 0.45;
      ctx.shadowBlur = 12;
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

  let refreshTimer = null;
  async function loadData(message = '') {
    const [sourceResult, graphResult] = await Promise.all([
      request(), request('/graph').catch(error => ({ nodes: [], edges: [], vectorAvailable: false, status: error.message }))
    ]);
    sources = sourceResult;
    graphData = graphResult;
    const pending = sources.some(source => source.active && source.indexStatus === 'PENDING');
    const detail = message || graphData.status || 'Đã tải nguồn tri thức.';
    refreshView(`${detail}${pending && graphData.vectorAvailable ? ' Đang chờ Gemini index; tự làm mới sau vài giây.' : ''}`);
    if (refreshTimer) clearTimeout(refreshTimer);
    if (pending && graphData.vectorAvailable) refreshTimer = setTimeout(() => loadData(), 5000);
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
  document.getElementById('ragReindexAll').addEventListener('click', async event => {
    const button = event.currentTarget;
    try {
      button.disabled = true;
      status.textContent = 'Đang lập chỉ mục các nguồn bằng Gemini…';
      const summary = await request('/reindex', { method: 'POST' });
      await loadData(`Đã xử lý ${summary.total} nguồn: ${summary.indexed} đã index, ${summary.pending} đang chờ, ${summary.failed} lỗi.`);
    } catch (error) { status.textContent = error.message; }
    finally { button.disabled = false; }
  });
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
        ? `${escapeRole(hoveredNode.node.role)} · ${hoveredNode.node.label} · ${hoveredNode.node.indexStatus || 'PENDING'} (${hoveredNode.node.indexedChunkCount || 0} đoạn)`
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
  loadData('Đang tải nguồn và láng giềng embedding…').catch(error => {
    status.textContent = error.message;
    document.getElementById('ragSourceCount').textContent = '0';
    document.getElementById('ragActiveCount').textContent = '0';
    document.getElementById('ragIndexedCount').textContent = '0';
    document.getElementById('ragEdgeCount').textContent = '0';
  });
})();
