(() => {
  const root = document.querySelector('.internal-chat');
  if (!root) return;
  const myRole = root.dataset.currentRole;
  const departments = [...root.querySelectorAll('.internal-chat__department')];
  const timeline = document.getElementById('chatTimeline');
  const title = document.getElementById('chatTitle');
  const avatar = document.getElementById('chatAvatar');
  const connection = document.getElementById('chatConnection');
  const form = document.getElementById('chatForm');
  const recipient = document.getElementById('chatRecipient');
  const body = document.getElementById('chatBody');
  const error = document.getElementById('chatError');
  let activeRole = null;
  let socket = null;
  let loadVersion = 0;
  const visibleIds = new Set();

  function showError(message) {
    error.textContent = message;
    error.hidden = !message;
  }

  function isCurrentConversation(message) {
    return (message.senderRole === myRole && message.recipientRole === activeRole)
      || (message.senderRole === activeRole && message.recipientRole === myRole);
  }

  function appendMessage(message) {
    if (!isCurrentConversation(message) || visibleIds.has(message.id)) return;
    visibleIds.add(message.id);
    timeline.querySelector('.internal-chat__empty')?.remove();
    const mine = message.senderRole === myRole;
    const article = document.createElement('article');
    article.className = 'internal-chat__bubble' + (mine ? ' internal-chat__bubble--mine' : '');
    const sender = document.createElement('strong');
    sender.textContent = mine ? 'Bạn' : message.senderName;
    const text = document.createElement('p');
    text.textContent = message.body;
    const time = document.createElement('time');
    time.textContent = message.createdAt;
    article.append(sender, text, time);
    timeline.append(article);
    timeline.scrollTop = timeline.scrollHeight;
  }

  function showEmpty(message) {
    timeline.replaceChildren();
    visibleIds.clear();
    const empty = document.createElement('div');
    empty.className = 'internal-chat__empty';
    empty.textContent = message;
    timeline.append(empty);
  }

  async function selectDepartment(button) {
    const role = button.dataset.role;
    if (!role) return;
    activeRole = role;
    recipient.value = role;
    departments.forEach(item => item.classList.toggle('is-active', item === button));
    button.querySelector('.internal-chat__unread').hidden = true;
    title.textContent = button.querySelector('strong').textContent;
    avatar.textContent = title.textContent.charAt(0);
    showError('');
    showEmpty('Đang tải hội thoại…');
    const version = ++loadVersion;
    try {
      const response = await fetch('/messages/api/' + encodeURIComponent(role), { headers: { Accept: 'application/json' } });
      if (!response.ok) throw new Error('Không tải được hội thoại.');
      const items = await response.json();
      if (version !== loadVersion) return;
      showEmpty('Chưa có tin nhắn. Hãy bắt đầu cuộc trò chuyện.');
      items.forEach(appendMessage);
    } catch (cause) {
      if (version === loadVersion) showEmpty(cause.message || 'Không tải được hội thoại.');
    }
  }

  departments.forEach(button => button.addEventListener('click', () => selectDepartment(button)));
  if (departments[0]) selectDepartment(departments[0]);

  form.addEventListener('submit', async event => {
    event.preventDefault();
    const text = body.value.trim();
    if (!text || !activeRole) return;
    const button = form.querySelector('button');
    button.disabled = true;
    showError('');
    try {
      const response = await fetch('/messages/api', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify({ recipientRole: activeRole, body: text })
      });
      const result = await response.json();
      if (!response.ok) throw new Error(result.error || 'Không gửi được tin nhắn.');
      appendMessage(result);
      body.value = '';
      body.focus();
    } catch (cause) {
      showError(cause.message || 'Không gửi được tin nhắn.');
    } finally {
      button.disabled = false;
    }
  });
  body.addEventListener('keydown', event => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      form.requestSubmit();
    }
  });

  function connect() {
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    socket = new WebSocket(protocol + '//' + location.host + '/ws-messages');
    socket.onopen = () => socket.send('CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n\0');
    socket.onmessage = event => {
      for (const raw of String(event.data).split('\0')) {
        const frame = raw.trimStart();
        if (frame.startsWith('CONNECTED')) {
          connection.textContent = 'Đã kết nối · Tin nhắn cập nhật trực tiếp';
          socket.send('SUBSCRIBE\nid:internal\ndestination:/topic/internal/' + myRole + '\nack:auto\n\n\0');
        } else if (frame.startsWith('MESSAGE')) {
          const split = frame.indexOf('\n\n');
          if (split < 0) continue;
          try {
            const message = JSON.parse(frame.slice(split + 2));
            if (isCurrentConversation(message)) appendMessage(message);
            else {
              const otherRole = message.senderRole === myRole ? message.recipientRole : message.senderRole;
              const department = departments.find(item => item.dataset.role === otherRole);
              if (department) department.querySelector('.internal-chat__unread').hidden = false;
            }
          } catch (_) { /* Ignore malformed frames. */ }
        }
      }
    };
    socket.onclose = () => {
      connection.textContent = 'Mất kết nối · Đang kết nối lại…';
      setTimeout(connect, 3000);
    };
    socket.onerror = () => socket.close();
  }
  connect();
})();
