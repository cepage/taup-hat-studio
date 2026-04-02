document.addEventListener('DOMContentLoaded', () => {
  const form = document.querySelector('.commissions-form');
  if (!form) return;

  form.addEventListener('submit', (e) => {
    e.preventDefault();

    const email = form.dataset.email;
    if (!email) return;

    const name = form.querySelector('[name="name"]')?.value || '';
    const senderEmail = form.querySelector('[name="email"]')?.value || '';
    const phone = form.querySelector('[name="phone"]')?.value || '';
    const type = form.querySelector('[name="commission-type"]')?.value || '';
    const description = form.querySelector('[name="description"]')?.value || '';
    const budget = form.querySelector('[name="budget"]')?.value || '';

    const body = [
      `Name: ${name}`,
      `Email: ${senderEmail}`,
      phone ? `Phone: ${phone}` : null,
      type ? `Commission Type: ${type}` : null,
      `\nDescription:\n${description}`,
      budget ? `\nBudget: ${budget}` : null,
    ]
      .filter(Boolean)
      .join('\n');

    const mailto = `mailto:${email}?subject=${encodeURIComponent('Commission Request')}&body=${encodeURIComponent(body)}`;
    window.location.href = mailto;
  });
});
