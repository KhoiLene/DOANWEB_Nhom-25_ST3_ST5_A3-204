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
    let history = [];
    let transcript = [];
    let storageKey = null;
    let ready = false;

    function saveState(open = !panel.hidden) {
      if (!storageKey) return;
      try {
        sessionStorage.setItem(storageKey, JSON.stringify({
          open,
          history: history.slice(-10),
          messages: transcript.slice(-40)
        }));
      } catch (_) { /* Storage can be unavailable in private browsing. */ }
    }

    function restoreState(context) {
      const user = document.getElementById('assistantUser')?.textContent?.trim() || 'guest';
      const role = String(context.roleKey || 'customer').replace(/[^a-z0-9_-]/gi, '');
      storageKey = `techtra.assistant.v1:${role}:${encodeURIComponent(user)}`;
      try {
        const saved = JSON.parse(sessionStorage.getItem(storageKey) || 'null');
        if (!saved || typeof saved !== 'object') return;
        history = Array.isArray(saved.history)
          ? saved.history.filter(turn => turn && ['user', 'assistant'].includes(turn.role) && typeof turn.content === 'string').slice(-10)
          : [];
        transcript = Array.isArray(saved.messages)
          ? saved.messages.filter(item => item && ['user', 'assistant'].includes(item.role) && typeof item.text === 'string').slice(-40)
          : [];
        transcript.forEach(item => addMessage(item.role, item.text, false, item.products));
        if (saved.open === true) setOpen(true, false);
      } catch (_) { history = []; transcript = []; }
    }

    function formatAnswer(text) {
      const raw = String(text || '');
      const sourceStart = raw.search(/(?:^|\r?\n)\s*(?:[-*•]\s*)?(?:nguồn(?: tham khảo)?|tài liệu tham khảo|references?)\s*:/im);
      const answer = sourceStart < 0 ? raw : raw.slice(0, sourceStart);
      const lines = answer.split(/\r?\n/).map(line => line
        .replace(/^\s*#{1,6}\s*/, '')
        .replace(/\*\*(.*?)\*\*/g, '$1')
        .replace(/\*(.*?)\*/g, '$1')
        .replace(/__(.*?)__/g, '$1')
        .replace(/_(.*?)_/g, '$1')
        .trim())
        .filter(Boolean);

      return lines.map(line => {
        const cleanLine = line.replace(/^(?:[-*•]|\d+[.)])\s+/, '').trim();
        return cleanLine.startsWith('→') ? cleanLine : `→ ${cleanLine}`;
      }).join('\n');
    }

    function addMessage(role, text, persist = true, products = []) {
      const item = document.createElement('article');
      item.className = `assistant-widget__message assistant-widget__message--${role}`;
      const content = document.createElement('p');
      content.textContent = role === 'assistant' ? formatAnswer(text) : text;
      item.append(content);
      if (role === 'assistant' && Array.isArray(products) && products.length) {
        const list = document.createElement('div');
        list.className = 'assistant-widget__products';
        products.slice(0, 3).forEach(product => {
          if (!Number.isInteger(Number(product.id))) return;
          const link = document.createElement('a');
          link.className = 'assistant-widget__product';
          link.href = `/product/${Number(product.id)}`;
          if (product.imageUrl) {
            const img = document.createElement('img');
            img.src = product.imageUrl;
            img.alt = product.name || 'Sản phẩm';
            img.loading = 'lazy';
            img.onerror = () => img.remove();
            link.append(img);
          }
          const details = document.createElement('span');
          const name = document.createElement('strong');
          name.textContent = product.name || 'Xem sản phẩm';
          const price = document.createElement('small');
          const amount = product.salePrice ?? product.price;
          price.textContent = (product.campaignAudience === 'VIP' ? 'Giá thường ' : '')
            + Number(amount).toLocaleString('vi-VN') + ' đ';
          details.append(name);
          if (product.salePrice != null) {
            const original = document.createElement('del');
            original.textContent = Number(product.price).toLocaleString('vi-VN') + ' đ';
            details.append(original);
          }
          if (product.discountPercent) {
            price.textContent += ` · Giảm ${product.discountPercent}%${product.campaignAudience === 'VIP' ? ' VIP khi đủ điểm' : ''}`;
          }
          details.append(price);
          link.append(details);
          list.append(link);
        });
        item.append(list);
      }
      messages.append(item);
      messages.scrollTop = messages.scrollHeight;
      if (persist && ['user', 'assistant'].includes(role)) {
        transcript.push({ role, text: String(text || ''), products: Array.isArray(products) ? products.slice(0, 3) : [] });
        transcript = transcript.slice(-40);
        saveState();
      }
      return item;
    }

    function setOpen(open, focus = true) {
      panel.hidden = !open;
      launcher.setAttribute('aria-expanded', String(open));
      saveState(open);
      if (open && focus) question.focus();
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
        restoreState(context);
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
      const loadingMessage = addMessage('assistant', 'Đang tìm câu trả lời', false);
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
        addMessage('assistant', result.answer, true, result.products);
        history.push({ role: 'user', content: text }, { role: 'assistant', content: result.answer });
        if (history.length > 10) history.splice(0, history.length - 10);
        saveState();
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
