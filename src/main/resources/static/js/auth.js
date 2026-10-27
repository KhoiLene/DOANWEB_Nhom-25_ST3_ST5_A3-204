(() => {
  document.querySelectorAll('[data-toggle]').forEach(button => {
    button.addEventListener('click', () => {
      const input = document.getElementById(button.dataset.toggle);
      if (!input) return;
      const visible = input.type === 'password';
      input.type = visible ? 'text' : 'password';
      button.setAttribute('aria-pressed', String(visible));
      button.setAttribute('aria-label', visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu');
      const icon = button.querySelector('i');
      if (icon) icon.className = visible ? 'fa-regular fa-eye-slash' : 'fa-regular fa-eye';
    });
  });

  const form = document.querySelector('.register-form');
  if (!form) return;
  const password = form.querySelector('#password');
  const confirmation = form.querySelector('#confirmPassword');
  if (!password || !confirmation) return;
  const validate = () => confirmation.setCustomValidity(
    confirmation.value && confirmation.value !== password.value ? 'Mật khẩu xác nhận chưa khớp.' : ''
  );
  password.addEventListener('input', validate);
  confirmation.addEventListener('input', validate);
  form.addEventListener('submit', event => {
    validate();
    if (!confirmation.checkValidity()) {
      event.preventDefault();
      confirmation.reportValidity();
    }
  });
})();
