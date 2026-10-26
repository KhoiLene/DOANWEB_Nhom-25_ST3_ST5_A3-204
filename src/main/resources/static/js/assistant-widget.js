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
      return item;
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
        document.getElementById('assistantName').textContent = 'Chatbot AI thông minh';
        document.getElementById('assistantDescription').textContent = 'Hỗ trợ theo vai trò của bạn';
        notice.textContent = context.ready
          ? 'Xin chào! Bạn có thể hỏi mình về sản phẩm hoặc các chức năng trong tài khoản.'
          : 'Chatbot đang tạm bận. Vui lòng thử lại sau.';
        ready = Boolean(context.ready);
        question.disabled = !ready;
        send.disabled = !ready;
      })
      .catch(() => {
        notice.textContent = 'Chatbot đang tạm bận. Vui lòng thử lại sau.';
        question.disabled = true;
        send.disabled = true;
      });

    form.addEventListener('submit', async event => {
      event.preventDefault();
      const text = question.value.trim();
      if (!text || !ready) return;
      addMessage('user', text);
      question.value = '';
      send.disabled = true;
      notice.textContent = 'Đang trả lời…';
      const loadingMessage = addMessage('assistant', 'Đang tìm câu trả lời');
      loadingMessage.classList.add('assistant-widget__message--loading');
      try {
        const response = await fetch('/api/assistant/chat', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
          body: JSON.stringify({ message: text, history: history.slice(-10) })
        });
        let result;
        try {
          result = await response.json();
        } catch (_) {
          throw new Error('Chatbot chưa thể trả lời lúc này. Bạn thử lại nhé.');
        }
        if (!response.ok) throw new Error(result.error || 'Trợ lý đang gặp sự cố.');
        loadingMessage.remove();
        addMessage('assistant', result.answer, result.sources || []);
        history.push({ role: 'user', content: text }, { role: 'assistant', content: result.answer });
        if (history.length > 10) history.splice(0, history.length - 10);
        notice.textContent = 'Bạn có thể hỏi tiếp.';
      } catch (error) {
        loadingMessage.remove();
        addMessage('assistant', error.message || 'Chatbot đang mất kết nối tạm thời. Bạn thử lại nhé.');
        notice.textContent = 'Bạn có thể gửi lại câu hỏi.';
      } finally {
        send.disabled = !ready;
        question.focus();
      }
    });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initAssistantWidget, { once: true });
  else initAssistantWidget();
})();
