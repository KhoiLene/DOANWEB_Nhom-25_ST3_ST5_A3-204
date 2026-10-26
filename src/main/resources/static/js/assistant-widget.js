(() => {
  function initAssistantWidget() {
    const root = document.getElementById('assistantWidget');
    if (!root) return;
    const launcher = document.getElementById('assistantLauncher');
    const panel = document.getElementById('assistantPanel');
    const messages = document.getElementById('assistantMessages');
    const form = document.getElementById('assistantForm');
    const question = document.getElementById('assistantQuestion');
    const send = document.getElementById('assistantSend');
    const notice = document.getElementById('assistantNotice');
    const history = [];
    let ready = false;

    function addMessage(role, text, sources = []) {
      const item = document.createElement('article');
      item.className = `assistant-widget__message assistant-widget__message--${role}`;
      const content = document.createElement('p');
      content.textContent = text;
      item.append(content);
      if (sources.length) {
        const sourceLine = document.createElement('small');
        sourceLine.textContent = `Nguồn: ${sources.join(', ')}`;
        item.append(sourceLine);
      }
      messages.append(item);
      messages.scrollTop = messages.scrollHeight;
    }

    function setOpen(open) {
      panel.hidden = !open;
      launcher.setAttribute('aria-expanded', String(open));
      if (open) question.focus();
    }

    launcher.addEventListener('click', () => setOpen(panel.hidden));
    document.getElementById('assistantClose').addEventListener('click', () => setOpen(false));
    question.addEventListener('keydown', event => {
      if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        form.requestSubmit();
      }
    });

    fetch('/api/assistant/context', { headers: { Accept: 'application/json' } })
      .then(response => {
        if (!response.ok) throw new Error('Không tải được trợ lý. Hãy đăng nhập lại.');
        return response.json();
      })
      .then(context => {
        document.getElementById('assistantName').textContent = context.name || 'Trợ lý Techtra';
        document.getElementById('assistantDescription').textContent = context.description || '';
        notice.textContent = context.ready
          ? `RAG cục bộ đã bật với ${context.sourceCount || 0} nguồn cho role này. ${context.generationMode || 'Câu trả lời dựa trên tài liệu được phép.'}`
          : 'Role này chưa có nguồn tri thức đang bật. Vui lòng liên hệ quản trị viên.';
        ready = Boolean(context.ready);
        question.disabled = !ready;
        send.disabled = !ready;
      })
      .catch(error => { notice.textContent = error.message; });

    form.addEventListener('submit', async event => {
      event.preventDefault();
      const text = question.value.trim();
      if (!text || !ready) return;
      addMessage('user', text);
      question.value = '';
      send.disabled = true;
      notice.textContent = 'Đang tìm tài liệu và soạn câu trả lời…';
      try {
        const response = await fetch('/api/assistant/chat', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
          body: JSON.stringify({ message: text, history: history.slice(-10) })
        });
        const result = await response.json();
        if (!response.ok) throw new Error(result.error || 'Trợ lý đang gặp sự cố.');
        addMessage('assistant', result.answer, result.sources || []);
        history.push({ role: 'user', content: text }, { role: 'assistant', content: result.answer });
        if (history.length > 10) history.splice(0, history.length - 10);
        notice.textContent = 'Bạn có thể hỏi tiếp.';
      } catch (error) {
        addMessage('assistant', error.message || 'Trợ lý đang gặp sự cố. Vui lòng thử lại.');
        notice.textContent = 'Câu hỏi chưa được xử lý.';
      } finally {
        send.disabled = !ready;
        question.focus();
      }
    });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initAssistantWidget, { once: true });
  else initAssistantWidget();
})();
